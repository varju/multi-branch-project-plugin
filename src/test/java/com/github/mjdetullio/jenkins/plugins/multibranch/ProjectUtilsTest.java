package com.github.mjdetullio.jenkins.plugins.multibranch;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class ProjectUtilsTest {
  @Test
  public void encodeAndDecode() {
    Map<String, String> mappings = new HashMap<String, String>();
    mappings.put("foo", "foo");
    mappings.put("foo/bar", "foo_PERCENT_2Fbar");
    mappings.put("foo%2Fbar", "foo_PERCENT_252Fbar");

    for (Map.Entry<String, String> mapping : mappings.entrySet()) {
      assertEquals("encoding " + mapping.getKey(), mapping.getValue(), ProjectUtils.encode(mapping.getKey()));
      assertEquals("decoding " + mapping.getValue(), mapping.getKey(), ProjectUtils.rawDecode(mapping.getValue()));
    }
  }
}
