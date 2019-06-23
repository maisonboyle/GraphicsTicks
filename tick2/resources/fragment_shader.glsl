#version 140

in vec3 frag_normal;	    // fragment normal in world space
in vec2 frag_texcoord;	// fragment texture coordinates in texture space
in vec3 pos;

out vec3 pixel_colour;

uniform sampler2D tex;  // 2D texture sampler
uniform vec3 camPos;

// Tone mapping and display encoding combined
vec3 tonemap( vec3 linearRGB )
{
    float L_white = 0.7; // Controls the brightness of the image

    float inverseGamma = 1./2.2;
    return pow( linearRGB/L_white, vec3(inverseGamma) ); // Display encoding - a gamma
}

void main()
{
	const vec3 I_a = vec3(0.01, 0.01, 0.01);       // Ambient light intensity (and colour)

	const float k_d = 0.6;                      // Diffuse light factor
    // vec3 C_diff = vec3(0.560, 0.525, 0.478);    // Diffuse material colour (TODO: replace with texture)
    vec4 texcolour = texture(tex, frag_texcoord);
    vec3 C_diff = texcolour.rgb;

	const vec3 I = vec3(0.941, 0.968, 1);   // Light intensity (and colour)
	vec3 L = normalize(vec3(2, 1.5, -0.5)); // The light direction as a unit vector
	vec3 N = frag_normal;                   // Normal in world coordinates

    // star: check height, make specular and white if high enough. Can comment out from 36 - 44. -o output.png for image
    vec3 specular = vec3(0.0);

    if (pos.y > 1.6f){
        C_diff = vec3(0.8);
        vec3 camDir = camPos - pos;
        vec3 camRef = 2*dot(N,camDir)*N - camDir;
        camRef = normalize(camRef);
        specular = 0.5*I*pow(max(0,dot(camRef,L)),3);
    }

	// TODO: Calculate colour using the illumination model
    vec3 ambient = C_diff * I_a;
    vec3 diffuse = C_diff * k_d * I * max(0,dot(L,N));
    vec3 linear_colour = ambient + diffuse + specular;



    pixel_colour = tonemap( linear_colour );
}
