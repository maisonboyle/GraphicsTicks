package uk.ac.cam.cl.gfxintro.dab80.tick2;

import org.joml.*;
import org.lwjgl.*;
import org.lwjgl.glfw.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.Math;
import java.nio.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.util.Arrays;
import java.util.function.*;

public class FunctionPlot {
    // Vertical field of view
    private static final float FOV_Y = (float) Math.toRadians(50);
    private static float HEIGHTMAP_SCALE = 1.0f;

    // Width and height of renderer in pixels
    protected static int WIDTH = 800, HEIGHT = 600;

    // Size of height map in world units
    private static float MAP_SIZE = 10;
    private Camera camera;
    private long window;

    private ShaderProgram shader1; // surface
    private ShaderProgram shader2; // black lines?

    private float[][] heightmap;
    private int no_of_triangles;
    int numLines;
    private int vertexArrayObj;
    int vertexArrayObj2; // for lines

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

    boolean squeeze = false;

    Matrix4f scale = new Matrix4f();

    public FunctionPlot( float l, float r, float b, float t, BiFunction<Float,Float,Float> f, float distance) {
        this.l = l;
        this.r = r;
        this.b = b;
        this.t = t;
        this.f = f;
        this.distance = distance;
        // Load heightmap data from file into CPU memory
        int density = 400;
        int w = (int)(200*(r-l));
        int h  = (int)(200*(t-b));
        initializeHeightmap(w,h);
    }

    public void initializeHeightmap(int w, int h) {
        heightmap = new float[h][w];
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                float x = ((float) row )/h;
                float y = ((float) col )/w;
                x = l+(r-l)*x;
                y = b+(t-b)*y;
                float height = f.apply(x,y);
                heightmap[row][col] = height;
                if ( height > maxY){
                    maxY = height;
                }
                if ( height < minY){
                    minY =  height;
                }
            }
        }
        if (maxY + minY != 0){
            float dif = (maxY + minY)/2;
            maxY -= dif;
            minY -= dif;
            for (int row = 0; row < h; row++) {
                for (int col = 0; col < w; col++) {
                    heightmap[row][col] -= dif;
                }
            }
        }

        if (squeeze) {
            float furthest = Math.max(r - l, t - b);
            if (maxY - minY > furthest * 3) {
                HEIGHTMAP_SCALE = furthest * 3 / (maxY - minY);
            } else if (3 * (maxY - minY) < furthest) {
                HEIGHTMAP_SCALE = furthest / (3 * (maxY - minY));
            }
            if (HEIGHTMAP_SCALE != 1.0f) {
                scaleHeight(HEIGHTMAP_SCALE);
            }
        }
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
        glEnable(GL_LINE_SMOOTH);
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
        shader1 = new ShaderProgram(vertShader, fragShader, "colour");
        shader2 = new ShaderProgram(vertShader, pointShader, "colour");


        // Initialize mesh data in CPU memory
        float vertPositions[] = initializeVertexPositions( heightmap );
        int surfaceIndices[] = initializeSurfaceIndices( heightmap );
        int lineIndices[] = initializeLineIndices(heightmap);
        float vertNormals[] = initializeVertexNormals( heightmap );
        no_of_triangles = surfaceIndices.length;
        numLines = lineIndices.length;
        // Load mesh data onto GPU memory
        loadDataOntoGPU( vertPositions, surfaceIndices, lineIndices, vertNormals );
    }

    public void loadDataOntoGPU( float[] vertPositions, int[] surfaceIndices, int[] lineIndices, float[] vertNormals ) {
        System.out.println("load Start");
        int s1_handle = shader1.getHandle();
        int s2_handle = shader2.getHandle();
        vertexArrayObj = glGenVertexArrays();
        vertexArrayObj2 = glGenVertexArrays();
        System.out.println("VAO 1 start");
        glBindVertexArray(vertexArrayObj);

        FloatBuffer vertex_buffer = BufferUtils.createFloatBuffer(vertPositions.length);
        vertex_buffer.put(vertPositions); // Put the vertex array into the CPU buffer
        vertex_buffer.flip(); // "flip" is used to change the buffer from write to read mode
        int vertex_handle = glGenBuffers(); // Get an OGL name for a buffer object
        glBindBuffer(GL_ARRAY_BUFFER, vertex_handle); // Bring that buffer object into existence on GPU
        glBufferData(GL_ARRAY_BUFFER, vertex_buffer, GL_STATIC_DRAW); // Load the GPU buffer object with data
        int position_loc = glGetAttribLocation(s1_handle, "position");
        glVertexAttribPointer(position_loc, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(position_loc);

        FloatBuffer normal_buffer = BufferUtils.createFloatBuffer(vertNormals.length); // create buffer correct size
        normal_buffer.put(vertNormals); // add data
        normal_buffer.flip(); // change write -> read
        int normal_handle = glGenBuffers(); // create reference to object
        glBindBuffer(GL_ARRAY_BUFFER, normal_handle); // create object
        glBufferData(GL_ARRAY_BUFFER, normal_buffer, GL_STATIC_DRAW); // load data
        int normal_loc = glGetAttribLocation(s1_handle, "normal"); // get attribute position
        glVertexAttribPointer(normal_loc, 3, GL_FLOAT, false, 0, 0); // where to find
        glEnableVertexAttribArray(normal_loc); // enable attribute

        IntBuffer index_buffer = BufferUtils.createIntBuffer(surfaceIndices.length);
        index_buffer.put(surfaceIndices).flip();
        int index_handle = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, index_handle);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, index_buffer, GL_STATIC_DRAW);

        checkError();

        System.out.println("VAO 2 start");
        glBindVertexArray(vertexArrayObj2);
        vertex_handle = glGenBuffers(); // Get an OGL name for a buffer object
        glBindBuffer(GL_ARRAY_BUFFER, vertex_handle); // Bring that buffer object into existence on GPU
        glBufferData(GL_ARRAY_BUFFER, vertex_buffer, GL_STATIC_DRAW); // Load the GPU buffer object with data
        position_loc = glGetAttribLocation(s2_handle, "position");
        glVertexAttribPointer(position_loc, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(position_loc);


        IntBuffer index_buffer2 = BufferUtils.createIntBuffer(lineIndices.length);
        index_buffer2.put(lineIndices).flip();
        index_handle = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, index_handle);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, index_buffer2, GL_STATIC_DRAW);
        System.out.println("VAO complete");
        glBindVertexArray(0); // Unbind the current vertex array

        // Finally, check for OpenGL errors
        checkError();
    }

    public void render() {
        shader1.forceReload();

        glClearColor(1.0f, 1.0f, 1.0f, 1.0f); // Set the background colour to white
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Step 2: Pass a new model-view-projection matrix to the vertex shader
        Matrix4f mvp_matrix; // Model-view-projection matrix
        mvp_matrix = new Matrix4f(camera.getProjectionMatrix()).mul(camera.getViewMatrix()).mul(scale);
        int mvp_location = glGetUniformLocation(shader1.getHandle(), "mvp_matrix");

        FloatBuffer mvp_buffer = BufferUtils.createFloatBuffer(16);
        mvp_matrix.get(mvp_buffer); // Put 16 floating point numbers with the matrix to the FloatBuffer
        glUniformMatrix4fv(mvp_location, false, mvp_buffer);

        // TODO: CHECK IF ATTRIBUTE ACTUALLY USED IN BOTH
        int rangeLocaiton = glGetUniformLocation(shader1.getHandle(), "minMax");

        glUniform2f(rangeLocaiton, minY, maxY);

        // Step 3: Draw our VertexArray as triangles
        glBindVertexArray(vertexArrayObj); // Bind the existing VertexArray object
        glDrawElements(GL_TRIANGLES, no_of_triangles, GL_UNSIGNED_INT, 0); // Draw it as triangles

        shader2.forceReload();
        int mvp_location2 = glGetUniformLocation(shader2.getHandle(), "mvp_matrix");
        glUniformMatrix4fv(mvp_location2, false, mvp_buffer);
        int rangeLocaiton2 = glGetUniformLocation(shader2.getHandle(), "minMax");
        glUniform2f(rangeLocaiton2, minY, maxY);

        glBindVertexArray(vertexArrayObj2); // Bind the existing VertexArray object
        // TODO: DIFFERENT LENGTH PARAMETER? e.g. no_of_lines
        glDrawElements(GL_LINES, numLines, GL_UNSIGNED_INT, 0); // Draw it as triangles


        glBindVertexArray(0);// Remove the binding

        checkError();
        // Step 4: Swap the draw and back buffers to display the rendered image

        glfwSwapBuffers(window);
        glfwPollEvents();
        checkError();
    }


    public int[] initializeLineIndices(float[][] heightmap){
        int scale = 20;
        int w = heightmap[0].length;
        int h = heightmap.length;
        int[] indices = new int[2*(2*h*w-h-w)+24];
        int count = 0;
        for (int row = 0; row < h; row+=scale){
            for (int col = 0; col < w-1; col++){
                int index = row*w + col;
                indices[count++] = index;
                indices[count++] = index+1;
            }
        }
        for (int row = 0; row < h-1; row++){
            for (int col = 0; col < w; col+=scale){
                int index = row*w + col;
                indices[count++] = index;
                indices[count++] = index+w;
            }
        }

        // TODO: ADD BOUNDARY LINES
        int finalIndex = w*h;
        for (int y = 0 ; y < 2; y++){
            indices[count++] = finalIndex + 4*y;
            indices[count++] = finalIndex+1 + 4*y;
            indices[count++] = finalIndex+1 + 4*y;
            indices[count++] = finalIndex+2 + 4*y;
            indices[count++] = finalIndex+2 + 4*y;
            indices[count++] = finalIndex+3 + 4*y;
            indices[count++] = finalIndex+3 + 4*y;
            indices[count++] = finalIndex + 4*y;
        }
        for (int x = 0; x < 4; x++){
            indices[count++] = finalIndex + x;
            indices[count++] = finalIndex + x + 4;
        }
        return indices;
    }

    public int[] initializeSurfaceIndices( float[][] heightmap ) {
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

    public float[] initializeVertexPositions( float[][] heightmap ) {
        //generate vertex data
        int heightmap_width_px = heightmap[0].length;
        int heightmap_height_px = heightmap.length;

        float start_x = l;
        float start_z = b;
        float delta_x = (r-l) / (heightmap_width_px-1);
        float delta_z = (t-b) / (heightmap_height_px-1);

        float[] vertPositions = new float[heightmap_height_px*heightmap_width_px*3 + 8*3]; // adds corner positions

        for (int row = 0; row < heightmap_height_px; row++) {
            for (int col = 0; col < heightmap_width_px; col++) {
                vertPositions[(row*heightmap_width_px + col)*3] = start_x + delta_x*col;
                vertPositions[(row*heightmap_width_px + col)*3 + 1] = heightmap[row][col];
                vertPositions[(row*heightmap_width_px + col)*3 + 2] = start_z + delta_z*row;
            }
        }
        int count = vertPositions.length - 24;

        // corners are lower then upper, start (-1,-1,-1) going anticlockwise about z
        float[] xOrder = {l,r,r,l};
        float[] zOrder = {b,b,t,t};
        float[] ys = {minY,maxY};
        for (float y : ys){
            for (int i = 0; i < 4; i++){
                vertPositions[count++] = xOrder[i];
                vertPositions[count++] = y;
                vertPositions[count++] = zOrder[i];
            }
        }
        return vertPositions;
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
                if (key == GLFW_KEY_P && action == GLFW_RELEASE) {
                    takeScreenshot("screenshot.png");
                } else if (key == GLFW_KEY_Z && action == GLFW_PRESS) {
                   // stretch
                    scale.scale(new Vector3f(1.f,1.05f,1.0f));

                } else if (key == GLFW_KEY_X && action == GLFW_PRESS) {
                    // compress
                    scale.scale(new Vector3f(1.f,0.95f,1.0f));
                }
                // TODO: ADD LOGIFY AND EXPIFY KEYS
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

    public void scaleHeight(float scale){
        for (int x = 0; x < heightmap[0].length; x++){
            for (int y = 0; y < heightmap.length; y++){
                heightmap[y][x] *= scale;
            }
        }
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

    public void run() {
        initializeOpenGL();
        while (!glfwWindowShouldClose(window)) {
            render();
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
