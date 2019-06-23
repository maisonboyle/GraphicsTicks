package uk.ac.cam.cl.gfxintro.dab80.tick2;

import java.util.function.*;

public class Tick2 {
    public static final String DEFAULT_INPUT = "functions/resources/mtsthelens.png";

    public static void usageError() {
        System.err.println("USAGE: <tick3> --input INPUT [--output OUTPUT]");
        System.exit(-1);
    }

    public static void main(String[] args) {
        // We should have an even number of arguments
        if (args.length % 2 != 0)
            usageError();

        String input = DEFAULT_INPUT, output = null;
        for (int i = 0; i < args.length; i += 2) {
            switch (args[i]) {
            case "-i":
            case "--input":
                input = args[i + 1];
                break;
            case "-o":
            case "--output":
                output = args[i + 1];
                break;
            default:
                System.err.println("unknown option: " + args[i]);
                usageError();
            }
        }

        if (input == null) {
            System.err.println("required arguments not present");
            usageError();
        }

        OpenGLApplication app = null;
        try {

            BiFunction<Float,Float,Float> f = (x,y) -> x*x + 4*x*y + y*y;
            float l = -3;
            float r = 3;
            float b = -3;
            float t = 3;
            float distancex = Math.max(l*l,r*r);
            float distancey = Math.max(t*t,b*b);
            // scaling guessed
            float distance = (float)Math.sqrt(distancex+distancey)*5;

            app = new OpenGLApplication(input, l, r, b, t, f, distance);

            if (output != null) {
                app.initializeOpenGL();
                app.takeScreenshot(output);
            } else {
                app.run();
            }
        } catch( RuntimeException ex ) {
            System.err.println( "RuntimeException: " + ex.getMessage() );
        } finally {
            if (app != null)
                app.stop();
        }
    }
}
