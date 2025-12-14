package su.terrafirmgreg.fieldguide.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import su.terrafirmgreg.fieldguide.data.tfc.page.TFCMultiblockData;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class TFCMultiblockDataAdapter extends TypeAdapter<TFCMultiblockData> {

    @Override
    public void write(JsonWriter out, TFCMultiblockData value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        if (value.getMultiblockId() != null && !value.getMultiblockId().isEmpty()) {
            out.value(value.getMultiblockId());
        } else {
            out.beginObject();
            if (value.getPattern() != null) {
                out.name("pattern");
                writePattern(out, value.getPattern());
            }
            if (value.getMapping() != null) {
                out.name("mapping");
                writeMapping(out, value.getMapping());
            }
            if (value.getSymmetrical() != null) {
                out.name("symmetrical").value(value.getSymmetrical());
            }
            if (value.getOffset() != null) {
                out.name("offset");
                writeOffset(out, value.getOffset());
            }
            out.endObject();
        }
    }

    @Override
    public TFCMultiblockData read(JsonReader in) throws IOException {

        switch (in.peek()) {
            case BEGIN_OBJECT: {
                return readMultiblockData(in);
            }
            case STRING: {
                return new TFCMultiblockData(in.nextString());
            }
            default: {
                in.skipValue();
                break;
            }
        }

        return null;
    }

    private TFCMultiblockData readMultiblockData(JsonReader in) throws IOException {
        in.beginObject();
        TFCMultiblockData data = new TFCMultiblockData();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "pattern":
                    data.setPattern(readPattern(in));
                    break;
                case "mapping":
                    data.setMapping(readMapping(in));
                    break;
                case "symmetrical":
                    data.setSymmetrical(in.nextBoolean());
                    break;
                case "offset":
                    data.setOffset(readOffset(in));
                    break;
                default:
                    in.skipValue();
                    break;
            }
        }
        in.endObject();

        return data;
    }

    private String[][] readPattern(JsonReader in) throws IOException {
        List<List<String>> patternList = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            List<String> row = new ArrayList<>();
            in.beginArray();
            while (in.hasNext()) {
                row.add(in.nextString());
            }
            in.endArray();
            patternList.add(row);
        }
        in.endArray();

        String[][] pattern = new String[patternList.size()][];
        for (int i = 0; i < patternList.size(); i++) {
            pattern[i] = patternList.get(i).toArray(new String[0]);
        }
        return pattern;
    }

    private Map<String, String> readMapping(JsonReader in) throws IOException {
        Map<String, String> mapping = new HashMap<>();
        in.beginObject();
        while (in.hasNext()) {
            String key = in.nextName();
            mapping.put(key, in.nextString());
        }
        in.endObject();
        return mapping;
    }

    private int[] readOffset(JsonReader in) throws IOException {
        List<Integer> offsetList = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            offsetList.add(in.nextInt());
        }
        in.endArray();
        int[] offset = new int[offsetList.size()];
        for (int i = 0; i < offsetList.size(); i++) {
            offset[i] = offsetList.get(i);
        }
        return offset;
    }

    private void writePattern(JsonWriter out, String[][] pattern) throws IOException {
        out.beginArray();
        for (String[] row : pattern) {
            out.beginArray();
            for (String cell : row) {
                out.value(cell);
            }
            out.endArray();
        }
        out.endArray();
    }

    private void writeMapping(JsonWriter out, Map<String, String> mapping) throws IOException {
        out.beginObject();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            out.name(entry.getKey()).value(entry.getValue());
        }
        out.endObject();
    }

    private void writeOffset(JsonWriter out, int[] offset) throws IOException {
        out.beginArray();
        for (int value : offset) {
            out.value(value);
        }
        out.endArray();
    }
}
