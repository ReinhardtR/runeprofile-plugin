package com.runeprofile.data;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class Manifest {
    final int version = -1;
    final Map<String, List<String>> pages = new HashMap<>();
}
