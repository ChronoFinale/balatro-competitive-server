-- BB.net — the MAIN-THREAD side of the threaded networking. The TCP socket itself lives on a love.thread
-- (networking/socket.lua); this module talks to it over two channels and correlates request/reply by seq.
-- The blocking-socket main-thread freeze (the #1 usability problem) goes away because the slow socket recv
-- happens on the thread: by the time a hook calls request(), the reply is usually already sitting in the
-- inbound channel, so the main-thread wait is one demand iteration, bounded by `timeout` (never the old
-- unbounded 2s socket wait).
--
-- Channels + json + clock are INJECTED (net.new), so the correlation/stash/drain logic is unit-testable
-- with fakes (test/net_spec.lua) — no love.thread or real socket needed to verify the bug-prone part.
--   out/inq : channel-likes with :push(v) and :pop()->v|nil and :demand(t)->v|nil
--   json    : { encode=fn, decode=fn }
--   now     : () -> seconds (love.timer.getTime in-game)

local net = {}
net.__index = net

function net.new(out, inq, json, now, opts)
	opts = opts or {}
	return setmetatable({
		out = out, inq = inq, json = json, now = now,
		seq = 2,                 -- auth(0)/newRun(1)/selectBlind(2) are reserved, matching the protocol
		stash = {},              -- inbound that wasn't the awaited reply (status/pushes/late) -> drain()
		connected = false,       -- updated as status envelopes pass through note()
		timeout = opts.timeout or 0.5,
		demandWait = opts.demandWait or 0.02,
	}, net)
end

function net:nextSeq()
	self.seq = self.seq + 1
	return self.seq
end

-- Apply a control/status envelope's side effects. Returns true if it was control (no app payload to deliver).
function net:note(env)
	if type(env) == "table" and env.action == "status" then
		self.connected = (env.state == "connected" or env.state == "authed")
		return true
	end
	return false
end

-- Read inbound until the reply with `seq` arrives (return its decoded envelope) or `timeout` elapses (nil).
-- Anything else is run through note() (connection state) and, if it's an app message, stashed for drain().
function net:await(seq, timeout)
	local deadline = self.now() + (timeout or self.timeout)
	while self.now() < deadline do
		local line = self.inq:demand(self.demandWait)
		if line then
			local ok, env = pcall(self.json.decode, line)
			if ok and type(env) == "table" then
				if env.seq == seq then
					self:note(env)
					return env
				end
				if not self:note(env) then self.stash[#self.stash + 1] = env end
			end
		end
	end
	return nil
end

-- Send an intent and await its seq-matched reply. `args` is merged into {type,seq}. Returns the decoded
-- envelope or nil on timeout (the caller's existing "no reply -> degrade" path handles nil).
function net:request(typ, args, timeout)
	local seq = self:nextSeq()
	local msg = { type = typ, seq = seq }
	if args then for k, v in pairs(args) do msg[k] = v end end
	self.out:push(self.json.encode(msg))
	return self:await(seq, timeout)
end

-- Fire-and-forget: send without awaiting a reply (the reply, if any, is picked up by drain()).
function net:send(typ, args)
	local msg = { type = typ, seq = self:nextSeq() }
	if args then for k, v in pairs(args) do msg[k] = v end end
	self.out:push(self.json.encode(msg))
end

-- Non-blocking: consume everything currently inbound (+ the stash), apply control side effects, and return
-- the app messages (unsolicited server pushes) for the caller to handle. Called each frame from Game:update.
function net:drain()
	local msgs = {}
	for _, env in ipairs(self.stash) do
		if not self:note(env) then msgs[#msgs + 1] = env end
	end
	self.stash = {}
	while true do
		local line = self.inq:pop()
		if not line then break end
		local ok, env = pcall(self.json.decode, line)
		if ok and type(env) == "table" and not self:note(env) then msgs[#msgs + 1] = env end
	end
	return msgs
end

return net
