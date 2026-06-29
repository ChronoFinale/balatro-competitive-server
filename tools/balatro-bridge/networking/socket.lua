-- networking/socket.lua — returns the SOURCE STRING for the networking love.thread (started by core/the
-- mod via love.thread.newThread(this_string):start(host, httpPort, tcpPort, username)). The thread OWNS the
-- TCP socket so the main render thread never blocks on I/O: it HTTP-logs-in for a token, connects + auths,
-- then pumps the bbridge_out channel to the socket and pushes received newline-delimited lines to
-- bbridge_in. It runs in a SEPARATE Lua state (only stdlib + socket + json + love available here).
-- Modeled on the Balatro Multiplayer mod's networking/socket.lua.
--
-- NOTE (in-game-unverified): non-blocking luasocket line reads need manual byte-buffering + \n splitting
-- (receive("*l") drops partials on timeout) — done below. Threading/socket timing still needs a real
-- in-game session to validate (the bbridge_in/out channel names must match what the mod wires up).

return [==[
local socket = require("socket")
local json = require("json")
local out = love.thread.getChannel("bbridge_out")
local inq = love.thread.getChannel("bbridge_in")
local host, httpPort, tcpPort, username = ...
host, httpPort, tcpPort = host or "127.0.0.1", httpPort or 28788, tcpPort or 28789
username = username or "balatro"

local function status(state) inq:push(json.encode({ action = "status", state = state })) end

-- Blocking HTTP POST /login -> token (on THIS thread, so the main thread never sees it).
local function http_login()
	local s = socket.tcp(); s:settimeout(2)
	if not s:connect(host, httpPort) then return nil end
	local body = json.encode({ username = username })
	s:send("POST /login HTTP/1.1\r\nHost: " .. host .. ":" .. httpPort ..
		"\r\nContent-Type: application/json\r\nContent-Length: " .. #body .. "\r\nConnection: close\r\n\r\n" .. body)
	local clen
	while true do
		local l = s:receive("*l")
		if not l or l == "" then break end
		local n = l:match("[Cc]ontent%-[Ll]ength:%s*(%d+)")
		if n then clen = tonumber(n) end
	end
	local resp = clen and s:receive(clen) or s:receive("*a")
	s:close()
	if not resp then return nil end
	local ok, t = pcall(json.decode, resp)
	return ok and t and t.token or nil
end

local conn
local function connect()
	status("connecting")
	local token = http_login()
	if not token then status("disconnected"); return false end
	local s = socket.tcp(); s:settimeout(2)
	if not s:connect(host, tcpPort) then status("disconnected"); return false end
	s:send(json.encode({ type = "auth", seq = 0, token = token }) .. "\n")
	local deadline = socket.gettime() + 5
	while socket.gettime() < deadline do
		local line = s:receive("*l")
		if line then
			local ok, env = pcall(json.decode, line)
			if ok and env and env.type == "authed" then
				conn = s; conn:settimeout(0); status("authed"); return true
			end
		end
	end
	s:close(); status("disconnected"); return false
end

local buf = ""
local function pump_in()
	while true do
		local chunk, err, partial = conn:receive(4096) -- non-blocking; partial holds bytes read before timeout
		local data = chunk or partial
		if data and #data > 0 then
			buf = buf .. data
		else
			if err and err ~= "timeout" then conn:close(); conn = nil; status("disconnected") end
			break
		end
	end
	-- emit every COMPLETE newline-delimited line; keep any trailing partial in buf for next pump
	while conn do
		local nl = buf:find("\n", 1, true)
		if not nl then break end
		inq:push(buf:sub(1, nl - 1))
		buf = buf:sub(nl + 1)
	end
end

local function pump_out()
	while conn do
		local line = out:pop()
		if not line then break end
		if not conn:send(line .. "\n") then conn:close(); conn = nil; status("disconnected"); break end
	end
end

local backoff = 2
while true do
	if not conn then
		buf = ""
		if connect() then backoff = 2 else socket.sleep(backoff); backoff = math.min(backoff * 2, 8) end
	else
		pump_out()
		if conn then pump_in() end
		socket.sleep(0.01)
	end
end
]==]
