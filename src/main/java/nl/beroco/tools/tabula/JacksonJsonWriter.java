package nl.beroco.tools.tabula;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import technology.tabula.Table;
import technology.tabula.writers.Writer;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class JacksonJsonWriter implements Writer {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void write(Appendable out, Table table) throws IOException {
        ObjectNode json = mapper.createObjectNode();
        json.put("simplewriter", "Not yet implemented");
        out.append(mapper.writeValueAsString(json));
    }

    @Override
    public void write(Appendable out, List<Table> list) throws IOException {
        Iterator<Table> it = list.iterator();
        out.append("[");
        while (it.hasNext()) {
            write(out, it.next());
            if (it.hasNext()) { out.append(",");}
        }
        out.append("]");
    }
}
