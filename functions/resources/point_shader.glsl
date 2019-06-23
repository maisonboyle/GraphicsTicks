#version 140

in vec3 pos;

out vec3 pixel_colour;

uniform vec2 minMax;

void main()
{

    pixel_colour = vec3(minMax.x);
}
