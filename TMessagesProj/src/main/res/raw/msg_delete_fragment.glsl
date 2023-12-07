#version 300 es

precision highp float;

uniform vec2 size;
uniform vec2 parts;
uniform float progress;
uniform sampler2D texture1;
uniform sampler2D texture2;

layout (location = 0) out vec4 fragColor;

void main() {
  // st range is 0 to 1
  vec2 st = gl_FragCoord.xy / size;
  // invert and shift up to support bmp
  st.y = -st.y + 1.0;

  vec4 tex2 = texture(texture2, st);
  // direction range is 0 to 1
  vec2 direction = tex2.xy;
  // up_speed range is 0 to 1
  float up_speed = tex2.z;
  // direction range becomes -1 to 1
  direction -= 0.5;
  direction *= 2.0;
  // random movement
  direction *= max(progress - st.x + 0.3, 0.0) * max(progress - parts.x, 0.0);
  // up movement
  direction.y -= max(progress - st.x + 0.1, 0.0) * max(progress - parts.x, 0.0) * up_speed;

  vec2 uv = st;
  //if (uv.x <= progress * 1.0) {
    uv -= direction;
  //}

  vec4 tex = texture(texture1, uv);

  // dissolve range is 0 to 1
  float dissolve = tex2.z;
  dissolve = step(progress, dissolve);
  if (uv.x <= progress) {
    tex.a *= dissolve;
  }

  // border
  vec2 border_uv = uv * 2.0 - 1.0;
  border_uv = clamp(abs(border_uv), 0.0, 1.0);
  float border = max(border_uv.x, border_uv.y);
  border = ceil(1.0 - border);
  tex.a *= border;

  fragColor = tex;
}
