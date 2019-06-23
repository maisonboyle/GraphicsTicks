package uk.ac.cam.cl.gfxintro.dab80.tick2;

import java.util.function.*;
import java.lang.Math;

public class FunctionMain {

    public static void usageError() {
        System.err.println("USAGE: <tick3> --input INPUT [--output OUTPUT]");
        System.exit(-1);
    }

    public static void main(String[] args) {
        // We should have an even number of arguments
        if (args.length % 2 != 0)
            usageError();
        String output = null;
        for (int i = 0; i < args.length; i += 2) {
            switch (args[i]) {
                case "-o":
                case "--output":
                    output = args[i + 1];
                    break;
                default:
                    System.err.println("unknown option: " + args[i]);
                    usageError();
            }
        }
        FunctionPlot app = null;
        try {
            BiFunction<Float,Float,Float> f = (x,y) -> x*x + 4*x*y + y*y;
            BiFunction<Float,Float,Float> f2 = (x,y) -> (float)Math.sin(10*(x*x+y*y))/5;
            BiFunction<Float,Float,Float> f3 = (x,y) -> (float)(Math.sin(5*x)*Math.cos(5*y)/5);
            BiFunction<Float,Float,Float> f4 = (x,y) -> (float)(4*4*x + 4*y*y + x*x*x*x - 6*x*x*y*y + y*y*y*y);
            BiFunction<Float,Float,Float> f5 = (x,y) -> (float)Math.pow(Math.sqrt(Math.abs(x))+Math.sqrt(Math.abs(y)),2);


            float r = 2;
            float t = 2;
            // UNEVEN
            float l = 0;
            float b = 0;


            float distancex = Math.max(l*l,r*r);
            float distancey = Math.max(t*t,b*b);
            // TODO: scaling guessed
            float distance = (float)Math.sqrt(distancex+distancey)*4;
            app = new FunctionPlot(l, r, b, t, f4, distance);
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
