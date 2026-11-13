package ml.dibidani.service;

import ml.dibidani.api.dto.PhotoStudioDtos.ProcessRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class PhotoProcessOptionsTest {
  @Test void validatesUserControlledProcessingOptions() {
    var json=new ObjectMapper();
    for (String invalid : new String[]{"{\"width\":90000}","{\"model\":\"https://evil\"}","{\"backgroundColor\":\"url(file)\"}","{\"rotation\":45}","{\"format\":\"svg\"}"}) {
      var request=json.readValue(invalid,ProcessRequest.class);
      assertThrows(IllegalArgumentException.class,()->PhotoProcessOptions.apply(new HashMap<>(),request));
    }
    var out=new HashMap<String,Object>();
    PhotoProcessOptions.apply(out,json.readValue("{\"background\":\"transparent\",\"width\":800,\"format\":\"webp\"}",ProcessRequest.class));
    assertEquals("rembg",out.get("engine")); assertEquals("transparent",out.get("background")); assertEquals(800,out.get("width"));
  }
}
