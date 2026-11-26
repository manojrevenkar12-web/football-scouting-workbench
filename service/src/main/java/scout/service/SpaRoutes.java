package scout.service;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The console's own routes load index.html, so a deep link or a reload opens the right page. Everything under /api stays the API. */
@Controller
public class SpaRoutes {
    @GetMapping({"/lab", "/assistant", "/players/{id:\\d+}"})
    public String index() {
        return "forward:/index.html";
    }
}
