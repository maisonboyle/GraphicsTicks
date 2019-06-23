#version 140

in vec3 frag_normal;	    // fragment normal in world space
in vec2 frag_texcoord;	// fragment texture coordinates in texture space
in vec3 pos;

out vec3 pixel_colour;

uniform vec2 minMax;

void main()
{
	const vec3 I_a = vec3(0.1, 0.1, 0.1);       // Ambient light intensity (and colour)

	const vec3 I = vec3(0.7);  // Light intensity (and colour)
	vec3 L = vec3(0, 1, 0); // The light direction as a unit vector
	vec3 N = frag_normal;                   // Normal in world coordinates

    float distance = (pos.y - minMax.x) / (minMax.y - minMax.x);
    vec3 colour = vec3(0.8,0.2,0.2) + vec3(0.2,0.8,0.6)*distance;

    vec3 ambient = colour * I_a;
    vec3 diffuse = colour * I * max(0,dot(L,N));
    vec3 linear_colour = ambient + diffuse;

    pixel_colour = linear_colour;
}
