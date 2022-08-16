///*
// * Copyright (c) 2020, Bram91
// * Copyright (c) 2020, Unmoon <https://github.com/Unmoon>
// * All rights reserved.
// *
// * Redistribution and use in source and binary forms, with or without
// * modification, are permitted provided that the following conditions are met:
// *
// * 1. Redistributions of source code must retain the above copyright notice, this
// *    list of conditions and the following disclaimer.
// * 2. Redistributions in binary form must reproduce the above copyright notice,
// *    this list of conditions and the following disclaimer in the documentation
// *    and/or other materials provided with the distribution.
// *
// * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
// * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
// */
//
//// This class was built by the amazing people over at: https://github.com/Bram91/Model-Dumper
//
//package com.runeprofile.playermodel;
//
//import net.runelite.api.Model;
//
//import java.awt.*;
//import java.io.PrintWriter;
//import java.io.StringWriter;
//import java.util.ArrayList;
//import java.util.List;
//
//public class PlayerModelFactory {
//	public static PlayerModel getPlayerModel(Model model, String username) {
//		if (model == null) {
//			return new PlayerModel("", "");
//		}
//
//		username = username.replace(" ", "_");
//
//		// Open writers
//		StringWriter objWriter = new StringWriter();
//		StringWriter mtlWriter = new StringWriter();
//		PrintWriter obj = new PrintWriter(objWriter);
//		PrintWriter mtl = new PrintWriter(mtlWriter);
//		obj.println("# Made by RuneLite Model-Dumper Plugin");
//		obj.println("o " + username);
//
//		// Write vertices
//		for (int vi = 0; vi < model.getVerticesCount(); ++vi) {
//			// Y and Z axes are flipped
//			int vx = model.getVerticesX()[vi];
//			int vy = -model.getVerticesY()[vi];
//			int vz = -model.getVerticesZ()[vi];
//			obj.println("v " + vx + " " + vy + " " + vz);
//		}
//
//		// Write faces
//		List<Color> knownColors = new ArrayList<>();
//		int prevMtlIndex = -1;
//		for (int fi = 0; fi < model.getFaceCount(); ++fi) {
//			// determine face color (textured or colored?)
//			Color c;
//			int textureId = -1;
//			if (model.getFaceTextures() != null) {
//				textureId = model.getFaceTextures()[fi];
//			}
//
//			if (textureId != -1) {
//				// get average color of texture
//				c = TextureColor.getColor(textureId);
//			} else {
//				// get average color of vertices
//				int c1 = model.getFaceColors1()[fi];
//				int c2 = model.getFaceColors2()[fi];
//				int c3 = model.getFaceColors3()[fi];
//				c = JagexColor.HSLtoRGBAvg(c1, c2, c3);
//			}
//
//			// see if our color already has a mtl
//			int ci = knownColors.indexOf(c);
//			if (ci == -1) {
//				// add to known colors
//				ci = knownColors.size();
//				knownColors.add(c);
//
//				// int to float color conversion
//				double r = (double) c.getRed() / 255.0d;
//				double g = (double) c.getGreen() / 255.0d;
//				double b = (double) c.getBlue() / 255.0d;
//
//				// write mtl
//				mtl.println("newmtl c" + ci);
//				mtl.printf("Kd %.4f %.4f %.4f\n", r, g, b);
//			}
//
//			// only write usemtl if the mtl has changed
//			if (prevMtlIndex != ci) {
//				obj.println("usemtl c" + ci);
//			}
//
//			// OBJ vertices are indexed by 1
//			int vi1 = model.getFaceIndices1()[fi] + 1;
//			int vi2 = model.getFaceIndices2()[fi] + 1;
//			int vi3 = model.getFaceIndices3()[fi] + 1;
//			obj.println("f " + vi1 + " " + vi2 + " " + vi3);
//
//			prevMtlIndex = ci;
//		}
//
//		obj.flush();
//		obj.close();
//
//		mtl.flush();
//		mtl.close();
//
//		return new PlayerModel(objWriter.toString(), mtlWriter.toString());
//	}
//}
