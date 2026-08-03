package com.runeprofile.modelexporter;

import net.runelite.api.Model;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Model} backed by a map of method name to return value, so a test can
 * describe the handful of faces it cares about without implementing the fifty
 * odd methods on the interface. Anything not supplied returns a zero value.
 */
final class FakeModel {
    private final Map<String, Object> values = new HashMap<>();

    static FakeModel builder() {
        return new FakeModel();
    }

    FakeModel faceCount(int count) {
        return set("getFaceCount", count);
    }

    FakeModel vertices(float[] x, float[] y, float[] z) {
        set("getVerticesX", x);
        set("getVerticesY", y);
        set("getVerticesZ", z);
        return set("getVerticesCount", x.length);
    }

    FakeModel faceIndices(int[] a, int[] b, int[] c) {
        set("getFaceIndices1", a);
        set("getFaceIndices2", b);
        return set("getFaceIndices3", c);
    }

    FakeModel faceColors(int[] a, int[] b, int[] c) {
        set("getFaceColors1", a);
        set("getFaceColors2", b);
        return set("getFaceColors3", c);
    }

    FakeModel faceTextures(short[] textures) {
        return set("getFaceTextures", textures);
    }

    FakeModel textureFaces(byte[] textureFaces, int[] a, int[] b, int[] c) {
        set("getTextureFaces", textureFaces);
        set("getTexIndices1", a);
        set("getTexIndices2", b);
        return set("getTexIndices3", c);
    }

    FakeModel faceTransparencies(byte[] transparencies) {
        return set("getFaceTransparencies", transparencies);
    }

    FakeModel faceBias(byte[] bias) {
        return set("getFaceBias", bias);
    }

    FakeModel facePriorities(byte[] priorities) {
        return set("getFaceRenderPriorities", priorities);
    }

    private FakeModel set(String method, Object value) {
        values.put(method, value);
        return this;
    }

    Model build() {
        final Map<String, Object> snapshot = new HashMap<>(values);
        return (Model) Proxy.newProxyInstance(
                Model.class.getClassLoader(),
                new Class<?>[]{Model.class},
                (proxy, method, args) -> {
                    if (snapshot.containsKey(method.getName())) {
                        return snapshot.get(method.getName());
                    }
                    final Class<?> returnType = method.getReturnType();
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == byte.class) {
                        return (byte) 0;
                    }
                    if (returnType == boolean.class) {
                        return false;
                    }
                    return null;
                });
    }
}
