package com.runeprofile.playermodel;

import net.runelite.api.Model;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class PLYExporter {
	public static byte[] export(Model model, String name) throws IOException {
		List<Vertex> vertices = new ArrayList<>();
		for (int fi = 0; fi < model.getFaceCount(); fi++) {
			// determine vertex colors (textured or colored?)
			Color vc1;
			Color vc2;
			Color vc3;
			int textureId = -1;
			if (model.getFaceTextures() != null)
				textureId = model.getFaceTextures()[fi];
			if (textureId != -1) {
				// get average color of texture
				vc1 = TextureColor.getColor(textureId);
				vc2 = vc1;
				vc3 = vc1;
			} else {
				// get average color of vertices
				vc1 = new Color(JagexColor.HSLtoRGB((short) model.getFaceColors1()[fi], JagexColor.BRIGHTNESS_MIN));
				vc2 = new Color(JagexColor.HSLtoRGB((short) model.getFaceColors2()[fi], JagexColor.BRIGHTNESS_MIN));
				vc3 = new Color(JagexColor.HSLtoRGB((short) model.getFaceColors3()[fi], JagexColor.BRIGHTNESS_MIN));
			}

			int vi1 = model.getFaceIndices1()[fi];
			int vi2 = model.getFaceIndices2()[fi];
			int vi3 = model.getFaceIndices3()[fi];

			int vx1 = model.getVerticesX()[vi1];
			int vx2 = model.getVerticesX()[vi2];
			int vx3 = model.getVerticesX()[vi3];
			int vy1 = -model.getVerticesY()[vi1];
			int vy2 = -model.getVerticesY()[vi2];
			int vy3 = -model.getVerticesY()[vi3];
			int vz1 = model.getVerticesZ()[vi1];
			int vz2 = model.getVerticesZ()[vi2];
			int vz3 = model.getVerticesZ()[vi3];

			vertices.add(new Vertex(vx1, vy1, vz1, vc1.getRed(), vc1.getGreen(), vc1.getBlue()));
			vertices.add(new Vertex(vx2, vy2, vz2, vc2.getRed(), vc2.getGreen(), vc2.getBlue()));
			vertices.add(new Vertex(vx3, vy3, vz3, vc3.getRed(), vc3.getGreen(), vc3.getBlue()));
		}

		ByteArrayOutputStream w = new ByteArrayOutputStream();
		w.write("ply".getBytes());
		w.write("format binary_little_endian 1.0".getBytes());
		w.write(("element vertex " + vertices.size()).getBytes());
		w.write("property int16 x".getBytes());
		w.write("property int16 y".getBytes());
		w.write("property int16 z".getBytes());
		w.write("property uint8 red".getBytes());
		w.write("property uint8 green".getBytes());
		w.write("property uint8 blue".getBytes());
		w.write(("element face " + model.getFaceCount()).getBytes());
		w.write("property list uint8 int16 vertex_indices".getBytes());
		w.write("end_header".getBytes());

		for (Vertex v : vertices) {
			// Y and Z axes are flipped
			w.write(le(v.x));
			w.write(le(v.z));
			w.write(le(v.y));
			w.write((byte) v.r);
			w.write((byte) v.g);
			w.write((byte) v.b);
		}

		for (int i = 0; i < model.getFaceCount(); ++i) {
			int vi = i * 3;
			w.write((byte) 3);
			w.write(le(vi));
			w.write(le(vi + 1));
			w.write(le(vi + 2));
		}

		w.flush();

		return w.toByteArray();
	}

	// int to little endian byte array
	private static byte[] le(int n) {
		byte[] b = new byte[2];
		b[0] = (byte) n;
		b[1] = (byte) (n >> 8);
		return b;
	}

	private static class Vertex {
		public int x, y, z;
		public int r, g, b;

		public Vertex(int x, int y, int z, int r, int g, int b) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.r = r;
			this.g = g;
			this.b = b;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Vertex vertex = (Vertex) o;
			return x == vertex.x && y == vertex.y && z == vertex.z && r == vertex.r && g == vertex.g && b == vertex.b;
		}
	}
}