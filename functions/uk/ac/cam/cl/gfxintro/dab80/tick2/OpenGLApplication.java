package uk.ac.cam.cl.gfxintro.dab80.tick2;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.util.function.*;

public class OpenGLApplication {

    // Vertical field of view
    private static final float FOV_Y = (float) Math.toRadians(50);
    private static final float HEIGHTMAP_SCALE = 1.0f;

    // Width and height of renderer in pixels
    protected static int WIDTH = 800, HEIGHT = 600;

    // Size of height map in world units
    private static float MAP_SIZE = 10;
    private Camera camera;
    private Texture terrainTexture;
    private long window;

    private ShaderProgram shaders;
    private ShaderProgram pointShaders;
    private float[][] heightmap;
    private int no_of_triangles;
    int numPoints;
    private int vertexArrayObj;

    // Callbacks for input handling
    private GLFWCursorPosCallback cursor_cb;
    private GLFWScrollCallback scroll_cb;
    private GLFWKeyCallback key_cb;

    // Filenames for vertex and fragment shader source code
    private final String VSHADER_FN = "functions/resources/vertex_shader.glsl";
    private final String FSHADER_FN = "functions/resources/fragment_shader.glsl";
    String PointShaderFile = "functions/resources/point_shader.glsl";

    float minY = Float.POSITIVE_INFINITY;
    float maxY = Float.NEGATIVE_INFINITY;

    float l,r,t,b,distance;
    BiFunction<Float,Float,Float> f;

    public OpenGLApplication(String heightmapFilename, float l, float r, float b, float t, BiFunction<Float,Float,Float> f, float distance) {
        this.l = l;
        this.r = r;
        this.b = b;
        this.t = t;
        this.f = f;
        this.distance = distance;
        // Load heightmap data from file into CPU memory
        initializeHeightmap(heightmapFilename);
    }

    // OpenGL setup - do not touch this method!
    public void initializeOpenGL() {

        if (glfwInit() != true)
            throw new RuntimeException("Unable to initialize the graphics runtime.");

        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        // Ensure that the right version of OpenGL is used (at least 3.2)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE); // Use CORE OpenGL profile without depreciated functions
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE); // Make it forward compatible

        window = glfwCreateWindow(WIDTH, HEIGHT, "Tick 3", NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create the application window.");

        GLFWVidMode mode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        glfwSetWindowPos(window, (mode.width() - WIDTH) / 2, (mode.height() - HEIGHT) / 2);
        glfwMakeContextCurrent(window);
        createCapabilities();

        // Enable v-sync
        glfwSwapInterval(1);

        // Cull back-faces of polygons
        // NOTE : CHANGED TO DRAW BOTH SIDES
       // glEnable(GL_CULL_FACE);
       // glCullFace(GL_BACK);
        glPointSize(4);

        // Do depth comparisons when rendering
        glEnable(GL_DEPTH_TEST);

        // Create camera, and setup input handlers
        camera = new Camera((double) WIDTH / HEIGHT, FOV_Y,distance);
        initializeInputs();

        // TODO: CHANGES MADE HERE
        // Create shaders and attach to a ShaderProgram
        Shader vertShader = new Shader(GL_VERTEX_SHADER, VSHADER_FN);
        Shader fragShader = new Shader(GL_FRAGMENT_SHADER, FSHADER_FN);
        Shader pointShader = new Shader(GL_FRAGMENT_SHADER, PointShaderFile);
        shaders = new ShaderProgram(vertShader, fragShader, "colour");
        pointShaders = new ShaderProgram(vertShader, pointShader, "colour");


        // Initialize mesh data in CPU memory
        float vertPositions[] = initializeVertexPositions( heightmap );
        int indices[] = initializeVertexIndices( heightmap );
        float vertNormals[] = initializeVertexNormals( heightmap );
        no_of_triangles = indices.length;

        // Load mesh data onto GPU memory
        loadDataOntoGPU( vertPositions, indices, vertNormals );

        // Load texture image and create OpenGL texture object
        terrainTexture = new Texture();
        terrainTexture.load("functions/resources/texture.png");
    }

    private void initializeInputs() {

        // Callback for: when dragging the mouse, rotate the camera
        cursor_cb = new GLFWCursorPosCallback() {
            private double prevMouseX, prevMouseY;

            public void invoke(long window, double mouseX, double mouseY) {
                boolean dragging = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
                if (dragging) {
                    camera.rotate(mouseX - prevMouseX, mouseY - prevMouseY);
                }
                prevMouseX = mouseX;
                prevMouseY = mouseY;
            }
        };
        // Callback for: when scrolling, zoom the camera
        scroll_cb = new GLFWScrollCallback() {
            public void invoke(long window, double dx, double dy) {
                camera.zoom(dy > 0);
            }
        };

        // Callback for keyboard controls: "W" - wireframe, "P" - points, "S" - take screenshot
        key_cb = new GLFWKeyCallback() {
            public void invoke(long window, int key, int scancode, int action, int mods) {
                if (key == GLFW_KEY_W && action == GLFW_PRESS) {
                    glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
                    glDisable(GL_CULL_FACE);
                } else if (key == GLFW_KEY_P && action == GLFW_PRESS) {
                    glPolygonMode(GL_FRONT_AND_BACK, GL_POINT);
                } else if (key == GLFW_KEY_S && action == GLFW_RELEASE) {
                    takeScreenshot("screenshot.png");
                } else if (action == GLFW_RELEASE) {
                    glEnable(GL_CULL_FACE);
                    glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
                }
            }
        };

        GLFWFramebufferSizeCallback fbs_cb = new GLFWFramebufferSizeCallback() {
            @Override
            public void invoke(long window, int width, int height) {
                glViewport( 0, 0, width, height );
                camera.setAspectRatio( width/height );
            }
        };

        // Set callbacks on the window
        glfwSetCursorPosCallback(window, cursor_cb);
        glfwSetScrollCallback(window, scroll_cb);
        glfwSetKeyCallback(window, key_cb);
        glfwSetFramebufferSizeCallback(window, fbs_cb);
    }

    /**
     * Create an array of vertex psoutions.
     *
     * @param heightmap 2D array with the heightmap
     * @return Vertex positions in the format { x0, y0, z0, x1, y1, z1, ... }
     */
    public float[] initializeVertexPositions( float[][] heightmap ) {
      //generate and upload vertex data

        int heightmap_width_px = heightmap[0].length;
        int heightmap_height_px = heightmap.length;

        float start_x = l;
        float start_z = b;
        float delta_x = (r-l) / (heightmap_width_px-1);
        float delta_z = (t-b) / (heightmap_height_px-1);


        float[] vertPositions = new float[heightmap_height_px*heightmap_width_px*3];



        for (int row = 0; row < heightmap_height_px; row++) {
            for (int col = 0; col < heightmap_width_px; col++) {
                vertPositions[(row*heightmap_width_px + col)*3] = start_x + delta_x*col;
                vertPositions[(row*heightmap_width_px + col)*3 + 1] = heightmap[row][col];
                vertPositions[(row*heightmap_width_px + col)*3 + 2] = start_z + delta_z*row;
            }
        }
        numPoints = vertPositions.length/3;
        return vertPositions;
    }

    public int[] initializeVertexIndices( float[][] heightmap ) {

        //generate and upload index data

        int heightmap_width_px = heightmap[0].length;
        int heightmap_height_px = heightmap.length;

        int[] indices = new int[6*(heightmap_height_px-1)*(heightmap_width_px-1)];

        int count = 0;
        for (int row = 0; row < heightmap_height_px - 1; row++) {
            for (int col = 0; col < heightmap_width_px - 1; col++) {
                int vertIndex = row*heightmap_width_px + col;
                indices[count++] = vertIndex;
                indices[count++] = vertIndex + heightmap_width_px;
                indices[count++] = vertIndex + heightmap_width_px + 1;
                indices[count++] = vertIndex;
                indices[count++] = vertIndex + heightmap_width_px + 1;
                indices[count++] = vertIndex + 1;
            }
        }
        return indices;
    }

    public float[] initializeVertexNormals( float[][] heightmap ) {



        int heightmap_width_px = heightmap[0].length;
        int heightmap_height_px = heightmap.length;

        int num_verts = heightmap_width_px * heightmap_height_px;
        float[] vertNormals = new float[3*num_verts];

        for (int index = 0; index < num_verts; index++){
            vertNormals[3*index + 1] = 1; // also using initialisation, sets each to 0,1,0
        }

        float delta_x = MAP_SIZE / heightmap_width_px;
        float delta_z = MAP_SIZE / heightmap_height_px;

        for (int row = 1; row < heightmap_height_px - 1; row++) {
            for (int col = 1; col < heightmap_width_px - 1; col++) {
                Vector3f Tx = new Vector3f(2*delta_x, heightmap[row][col+1] - heightmap[row][col-1], 0);
                Vector3f Tz = new Vector3f(0, heightmap[row+1][col] - heightmap[row-1][col], 2*delta_z); // try switching

                Vector3f N = new Vector3f();

                Tz.cross(Tx,N); // Tx x Tz, store in N
                N = N.normalize();

                vertNormals[3*(row*heightmap_width_px + col)] = N.x;
                vertNormals[3*(row*heightmap_width_px + col)+1] = N.y;
                vertNormals[3*(row*heightmap_width_px + col)+2] = N.z;
            }
        }

        return vertNormals;
    }

    public float[][] getHeightmap() {
        return heightmap;
    }

    public void initializeHeightmap(String heightmapFilename) {

        try {
            BufferedImage heightmapImg = ImageIO.read(new File(heightmapFilename));
            int heightmap_width_px = heightmapImg.getWidth();
            int heightmap_height_px = heightmapImg.getHeight();

            heightmap = new float[heightmap_height_px][heightmap_width_px];

            for (int row = 0; row < heightmap_height_px; row++) {
                for (int col = 0; col < heightmap_width_px; col++) {
                    //float height = (float) (heightmapImg.getRGB(col, row) & 0xFF) / 0xFF;
                    float x = ((float) row )/heightmap_height_px;
                    float y = ((float) col )/heightmap_width_px;
                    x = l+(r-l)*x;
                    y = b+(t-b)*y;
                    float height = f.apply(x,y);
                    //heightmap[row][col] = HEIGHTMAP_SCALE * (float) Math.pow(height, 2.2);
                    heightmap[row][col] = HEIGHTMAP_SCALE * height;
                    if (HEIGHTMAP_SCALE * height > maxY){
                        maxY = HEIGHTMAP_SCALE * height;
                    }
                    if (HEIGHTMAP_SCALE * height < minY){
                        minY = HEIGHTMAP_SCALE * height;
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error loading heightmap");
        }
    }


    public void loadPointData (){
        // TODO: Repeat for point shader
        // SAME FOR POINTS
        float[] pointPos = new float[] {0,0,0,1,1,1,2,2,2,3,3,3};
        numPoints = 4;
        int point_handle = pointShaders.getHandle();
        FloatBuffer point_vertex_buffer = BufferUtils.createFloatBuffer(pointPos.length);
        point_vertex_buffer.put(pointPos); // Put the vertex array into the CPU buffer
        point_vertex_buffer.flip(); // "flip" is used to change the buffer from write to read mode

        int point_vertex_handle = glGenBuffers(); // Get an OGL name for a buffer object
        glBindBuffer(GL_ARRAY_BUFFER, point_vertex_handle); // Bring that buffer object into existence on GPU
        glBufferData(GL_ARRAY_BUFFER, point_vertex_buffer, GL_STATIC_DRAW); // Load the GPU buffer object with data

        int position_loc2 = glGetAttribLocation(point_handle, "position");
        glVertexAttribPointer(position_loc2, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(position_loc2);
    }

    public void loadDataOntoGPU( float[] vertPositions, int[] indices, float[] vertNormals ) {

        // TODO: IS THIS THE RIGHT PLACE

        // TODO: THIS MAY BE THE PLACE TO CHANGE
        int shaders_handle = shaders.getHandle();
        int point_handle = pointShaders.getHandle();
        // may need second glGenVertexArrays() for second type of drawing
        vertexArrayObj = glGenVertexArrays(); // Get a OGL "name" for a vertex-array object
        glBindVertexArray(vertexArrayObj); // Create a new vertex-array object with that name

        // ---------------------------------------------------------------
        // LOAD VERTEX POSITIONS
        // ---------------------------------------------------------------

        // Construct the vertex buffer in CPU memory
        FloatBuffer vertex_buffer = BufferUtils.createFloatBuffer(vertPositions.length);
        vertex_buffer.put(vertPositions); // Put the vertex array into the CPU buffer
        vertex_buffer.flip(); // "flip" is used to change the buffer from write to read mode

        int vertex_handle = glGenBuffers(); // Get an OGL name for a buffer object
        glBindBuffer(GL_ARRAY_BUFFER, vertex_handle); // Bring that buffer object into existence on GPU
        glBufferData(GL_ARRAY_BUFFER, vertex_buffer, GL_STATIC_DRAW); // Load the GPU buffer object with data


        // Get the locations of the "position" vertex attribute variable in our ShaderProgram
        int position_loc = glGetAttribLocation(shaders_handle, "position");

        // If the vertex attribute does not exist, position_loc will be -1
        if (position_loc == -1)
            throw new RuntimeException( "'position' variable not found in the shader file");

        // Specifies where the data for "position" variable can be accessed
        glVertexAttribPointer(position_loc, 3, GL_FLOAT, false, 0, 0);


        // Enable that vertex attribute variable
        glEnableVertexAttribArray(position_loc);




        // ---------------------------------------------------------------
        // LOAD VERTEX NORMALS
        // ---------------------------------------------------------------
        FloatBuffer normal_buffer = BufferUtils.createFloatBuffer(vertNormals.length); // create buffer correct size
        normal_buffer.put(vertNormals); // add data
        normal_buffer.flip(); // change write -> read

        int normal_handle = glGenBuffers(); // create reference to object
        glBindBuffer(GL_ARRAY_BUFFER, normal_handle); // create object
        glBufferData(GL_ARRAY_BUFFER, normal_buffer, GL_STATIC_DRAW); // load data

        int normal_loc = glGetAttribLocation(shaders_handle, "normal"); // get attribute position
        int normal_loc2 = glGetAttribLocation(point_handle, "normal");
        if (normal_loc != -1){ // exists
            glVertexAttribPointer(normal_loc, 3, GL_FLOAT, false, 0, 0); // where to find
            glEnableVertexAttribArray(normal_loc); // enable attribute
        }

        // ---------------------------------------------------------------
        // LOAD VERTEX INDICES
        // ---------------------------------------------------------------

        IntBuffer index_buffer = BufferUtils.createIntBuffer(indices.length);
        index_buffer.put(indices).flip();
        int index_handle = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, index_handle);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, index_buffer, GL_STATIC_DRAW);

        glBindVertexArray(0); // Unbind the current vertex array

        // Finally, check for OpenGL errors
        checkError();
    }


    public void run() {

        initializeOpenGL();

        while (!glfwWindowShouldClose(window)) {
            render();
        }
    }

    public void render() {
        // TODO: May also need to set shader here/change it
        shaders.reloadIfNeeded(); // If shaders modified on disk, reload them
        shaders.forceReload();
        //pointShaders.reloadIfNeeded();
        // Step 1: Clear the buffer

        glClearColor(1.0f, 1.0f, 1.0f, 1.0f); // Set the background colour to white
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Step 2: Pass a new model-view-projection matrix to the vertex shader

        Matrix4f mvp_matrix; // Model-view-projection matrix
        mvp_matrix = new Matrix4f(camera.getProjectionMatrix()).mul(camera.getViewMatrix());

        int mvp_location = glGetUniformLocation(shaders.getHandle(), "mvp_matrix");
       // int mvp_location2 = glGetUniformLocation(pointShaders.getHandle(), "mvp_matrix");
        FloatBuffer mvp_buffer = BufferUtils.createFloatBuffer(16);
        mvp_matrix.get(mvp_buffer); // Put 16 floating point numbers with the matrix to the FloatBuffer
        glUniformMatrix4fv(mvp_location, false, mvp_buffer);
        //glUniformMatrix4fv(mvp_location2, false, mvp_buffer);


        int rangeLocaiton = glGetUniformLocation(shaders.getHandle(), "minMax");
        glUniform2f(rangeLocaiton, minY, maxY);

        // Step 3: Draw our VertexArray as triangles

        glBindVertexArray(vertexArrayObj); // Bind the existing VertexArray object

        // TODO: PUT BACK
        glDrawElements(GL_TRIANGLES, no_of_triangles, GL_UNSIGNED_INT, 0); // Draw it as triangles

        // TODO: FIX THIS
        //pointShaders.reloadIfNeeded();

        //glDrawArrays(GL_POINTS,0,numPoints);

        // END OF CHANGES

        glBindVertexArray(0);// Remove the binding

        // TODO: MESS WITH STUFF
      /*  pointShaders.reloadIfNeeded();
        glBindVertexArray(vertexArrayObj);
        glDrawArrays(GL_POINTS,0,numPoints);
        glBindVertexArray(0);
*/


        // draw extra components
        checkError();
        // Step 4: Swap the draw and back buffers to display the rendered image

        //glfwSwapBuffers(window);
        glfwPollEvents();
        checkError();
        renderPoints();
    }

    public void renderPoints() {
        // TODO: May also need to set shader here/change it
        pointShaders.forceReload(); // If shaders modified on disk, reload them
        //pointShaders.reloadIfNeeded();
        // Step 1: Clear the buffer
        // Step 2: Pass a new model-view-projection matrix to the vertex shader

        Matrix4f mvp_matrix; // Model-view-projection matrix
        mvp_matrix = new Matrix4f(camera.getProjectionMatrix()).mul(camera.getViewMatrix());

        int mvp_location2 = glGetUniformLocation(pointShaders.getHandle(), "mvp_matrix");
        FloatBuffer mvp_buffer = BufferUtils.createFloatBuffer(16);
        mvp_matrix.get(mvp_buffer); // Put 16 floating point numbers with the matrix to the FloatBuffer
        glUniformMatrix4fv(mvp_location2, false, mvp_buffer);

        //int rangeLocaiton = glGetUniformLocation(pointShaders.getHandle(), "minMax");
        //glUniform2f(rangeLocaiton, minY, maxY);
        // Step 3: Draw our VertexArray as triangles

        glBindVertexArray(vertexArrayObj); // Bind the existing VertexArray object

        // TODO: FIX THIS

        //glPointSize(4);
        glDrawArrays(GL_POINTS,0,numPoints);
        //glDrawArrays(GL_LINES,0,numPoints/2);

        // END OF CHANGES

        glBindVertexArray(0);// Remove the binding

        // draw extra components
        checkError();
        // Step 4: Swap the draw and back buffers to display the rendered image

        glfwSwapBuffers(window);
        glfwPollEvents();
        checkError();
    }

    public void takeScreenshot(String output_path) {
        int bpp = 4;

        // Take screenshot of the fixed size irrespective of the window resolution
        int screenshot_width = 800;
        int screenshot_height = 600;

        int fbo = glGenFramebuffers();
        glBindFramebuffer( GL_FRAMEBUFFER, fbo );

        int rgb_rb = glGenRenderbuffers();
        glBindRenderbuffer( GL_RENDERBUFFER, rgb_rb );
        glRenderbufferStorage( GL_RENDERBUFFER, GL_RGBA, screenshot_width, screenshot_height );
        glFramebufferRenderbuffer( GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, rgb_rb );

        int depth_rb = glGenRenderbuffers();
        glBindRenderbuffer( GL_RENDERBUFFER, depth_rb );
        glRenderbufferStorage( GL_RENDERBUFFER, GL_DEPTH_COMPONENT, screenshot_width, screenshot_height );
        glFramebufferRenderbuffer( GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth_rb );
        checkError();

        float old_ar = camera.getAspectRatio();
        camera.setAspectRatio( (float)screenshot_width  / screenshot_height );
        glViewport(0,0, screenshot_width, screenshot_height );
        render();
        camera.setAspectRatio( old_ar );

        glReadBuffer(GL_COLOR_ATTACHMENT0);
        ByteBuffer buffer = BufferUtils.createByteBuffer(screenshot_width * screenshot_height * bpp);
        glReadPixels(0, 0, screenshot_width, screenshot_height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        checkError();

        glBindFramebuffer( GL_FRAMEBUFFER, 0 );
        glDeleteRenderbuffers( rgb_rb );
        glDeleteRenderbuffers( depth_rb );
        glDeleteFramebuffers( fbo );
        checkError();

        BufferedImage image = new BufferedImage(screenshot_width, screenshot_height, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < screenshot_width; ++i) {
            for (int j = 0; j < screenshot_height; ++j) {
                int index = (i + screenshot_width * (screenshot_height - j - 1)) * bpp;
                int r = buffer.get(index + 0) & 0xFF;
                int g = buffer.get(index + 1) & 0xFF;
                int b = buffer.get(index + 2) & 0xFF;
                image.setRGB(i, j, 0xFF << 24 | r << 16 | g << 8 | b);
            }
        }
        try {
            ImageIO.write(image, "png", new File(output_path));
        } catch (IOException e) {
            throw new RuntimeException("failed to write output file - ask for a demonstrator");
        }
    }

    public void stop() {
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void checkError() {
        int error = glGetError();
        if (error != GL_NO_ERROR)
            throw new RuntimeException("OpenGL produced an error (code " + error + ") - ask for a demonstrator");
    }
}
