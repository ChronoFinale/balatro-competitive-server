-- Probe: can LÖVE init a graphics/GL context with no real display?
-- This is the gating question for running modded Balatro headless on a server.
function love.load()
  io.stdout:setvbuf("no")
  local maj, min, rev, code = love.getVersion()
  print(string.format("LOVE core boot OK: %d.%d.%d (%s)", maj, min, rev, code))
  print("video driver: " .. tostring(os.getenv("SDL_VIDEODRIVER") or "(default)"))

  if love.graphics then
    local ok, a, b = pcall(function() return love.graphics.getRendererInfo() end)
    print("getRendererInfo ok=" .. tostring(ok) .. "  renderer=" .. tostring(a))
    -- A Canvas is an offscreen GL framebuffer — proves we can actually render.
    local cok, canvas = pcall(love.graphics.newCanvas, 64, 64)
    print("offscreen Canvas ok=" .. tostring(cok))
  else
    print("love.graphics module: DISABLED")
  end
  love.event.quit(0)
end

function love.draw() end
function love.errorhandler(msg)
  print("LOVE ERRORHANDLER: " .. tostring(msg))
  return function() return 1 end
end
