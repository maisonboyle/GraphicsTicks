#version 140
const vec3 BLACK = vec3(0, 0, 0);
const vec3 WHITE = vec3(1, 1, 1);
const vec3 BLUE = vec3(0, 0, 1);
in vec3 frag_normal;	    // fragment normal in world space
in vec2 frag_texcoord;		// fragment texture coordinates in texture space
in vec3 pos;
const vec3 lightPosition = vec3(5,20,5);

out vec3 pixel_colour;

uniform vec3 eyePosition;

uniform sampler2D tex;  // 2D texture sampler

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
    vec3 C_diff = vec3(0.560, 0.525, 0.478);    // Diffuse material colour (TODO: replace with texture)

	const vec3 I = vec3(0.941, 0.968, 1);   // Light intensity (and colour)
	vec3 L = normalize(vec3(2, 1.5, -0.5)); // The light direction as a unit vector
	vec3 N = frag_normal;                   // Normal in world coordinates

    vec3 color = 0.8 * WHITE;

	// TODO: Calculate colour using the illumination model
    //vec3 linear_colour = abs(frag_normal); // TODO: replace this line
    //vec3 lightVector = lightPosition - pos;
    //lightVector = normalize(lightVector);
    //vec3 linear_colour = 0.9 * WHITE * max(0,dot(frag_normal,lightVector));
    vec3 linear_colour = WHITE;

    vec3 rayDir = eyePosition - pos;
    rayDir = normalize(rayDir);
    vec3 d = eyePosition - dot(eyePosition, rayDir) * rayDir;
    bool sphere = false;
    if ((length(d)) <= 1.3){
        vec3 intersect = d + sqrt(1.69 - pow(length(d),2)) * rayDir;
        if (length(eyePosition - intersect) < length(eyePosition - pos)){
        sphere = true;
        vec3 sphereNorm = normalize(intersect);
        vec3 lightVector = lightPosition - intersect;
        lightVector = normalize(lightVector);
        vec3 diffuse = 0.75 * BLUE * max(0,dot(sphereNorm,lightVector));
        vec3 camVector = eyePosition - intersect;
        camVector = normalize(camVector);
        vec3 reflection = 2 * dot(sphereNorm, camVector) * sphereNorm - camVector;
        vec3 specular = 0.25 * BLUE * max(0,dot(reflection,lightVector));
        linear_colour = diffuse + specular;
        }
    } if (sphere == false){



    int edges;
    if (abs(pos.x) > 0.9){
        edges++;
    }if (abs(pos.y) > 0.9){
        edges++;
    }if (abs(pos.z) > 0.9){
        edges++;
    }
    if (edges>1){
        linear_colour = BLACK;
    }else{
    vec3 lightVector = lightPosition - pos;
    lightVector = normalize(lightVector);
    vec3 diffuse = 0.75 * color * max(0,dot(frag_normal,lightVector));
    vec3 camVector = eyePosition - pos;
    camVector = normalize(camVector);
    vec3 reflection = 2 * dot(frag_normal, camVector) * frag_normal - camVector;
    vec3 specular = 0.25 * color * max(0,dot(reflection,lightVector));
    color = diffuse + specular;
    linear_colour = color;
}}

    pixel_colour = tonemap( linear_colour );
}
