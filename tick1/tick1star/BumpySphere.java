package uk.ac.cam.cl.gfxintro.dab80.tick1star;

import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class BumpySphere extends Sphere {

	private float BUMP_FACTOR = 5f;
	private float[][] bumpMap;
	private int bumpMapHeight;
	private int bumpMapWidth;

	public BumpySphere(Vector3 position, double radius, ColorRGB colour, String bumpMapImg) {
		super(position, radius, colour);
		try {
			BufferedImage inputImg = ImageIO.read(new File(bumpMapImg));
			bumpMapHeight = inputImg.getHeight();
			bumpMapWidth = inputImg.getWidth();
			bumpMap = new float[bumpMapHeight][bumpMapWidth];
			for (int row = 0; row < bumpMapHeight; row++) {
				for (int col = 0; col < bumpMapWidth; col++) {
					float height = (float) (inputImg.getRGB(col, row) & 0xFF) / 0xFF;
					bumpMap[row][col] = BUMP_FACTOR * height;
				}
			}
		} catch (IOException e) {
			System.err.println("Error creating bump map");
			e.printStackTrace();
		}
	}

	// Get normal to surface at position
	@Override
	public Vector3 getNormalAt(Vector3 position) {
		Vector3 N = position.subtract(this.getPosition()).normalised();
		double v = Math.atan2(Math.sqrt(N.x*N.x + N.z*N.z),N.y)/Math.PI;
		double u = Math.atan2(N.x,N.z)/(2*Math.PI) + 0.5;
		
		Vector3 Pv = N.cross(new Vector3(0,1,0)).normalised();
		Vector3 Pu = N.cross(Pv).normalised();
		
		int upix = (int)(u*bumpMapWidth);
		int vpix = (int)(v*bumpMapHeight);
		
		double Bu;
		if (upix < bumpMapWidth - 1){
			Bu = bumpMap[vpix][upix] - bumpMap[vpix][upix+1];
		}else{
			Bu = bumpMap[vpix][upix-1] - bumpMap[vpix][upix];
		}
		double Bv;
		if (vpix < bumpMapHeight - 1){
			Bv = bumpMap[vpix][upix] - bumpMap[vpix+1][upix];
		}else{
			Bv = bumpMap[vpix-1][upix] - bumpMap[vpix][upix];
		}
		return N.add(N.cross(Pv).scale(Bu)).add(N.cross(Pu).scale(Bv)).normalised(); //TODO: return the normal modified by the bump map

	}
}
