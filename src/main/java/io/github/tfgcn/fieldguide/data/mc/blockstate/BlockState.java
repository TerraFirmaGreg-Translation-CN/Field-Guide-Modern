package io.github.tfgcn.fieldguide.data.mc.blockstate;

import com.google.gson.annotations.JsonAdapter;
import io.github.tfgcn.fieldguide.exception.InternalException;
import io.github.tfgcn.fieldguide.gson.BlockStateVariantMapAdapter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@Data
public class BlockState {

    @JsonAdapter(BlockStateVariantMapAdapter.class)
    private LinkedHashMap<String, List<Variant>> variants;
    private List<MultiPartCase> multipart;

    public boolean hasVariants() {
        return variants != null && !variants.isEmpty();
    }

    public boolean hasMultipart() {
        return multipart != null && !multipart.isEmpty();
    }

    public static Map<String, String> parseBlockProperties(String properties) {
        Map<String, String> state = new HashMap<>();
        if (properties.contains("=")) {
            String[] pairs = properties.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                state.put(keyValue[0], keyValue[1]);
            }
        }
        return state;
    }

    public List<Variant> selectByVariants(Map<String, String> state) {
        List<Variant> defaultVariants = variants.values().stream().filter(Objects::nonNull).findFirst().orElse(null);

        for (Map.Entry<String, List<Variant>> entry : variants.entrySet()) {
            String key = entry.getKey();
            List<Variant> value = entry.getValue();

            Map<String, String> properties = parseBlockProperties(key);
            if (state.entrySet().containsAll(properties.entrySet())) {
                return value;
            }
        }

        // FIXME 改为扫描多个blockState文件
//        if (defaultVariants != null && !defaultVariants.isEmpty()) {
//            return defaultVariants;
//        }
        log.warn("No variants found, state:{} in variants: {}", state, variants.keySet());

        if (hasMultipart()) {
            return selectByMultipart(state);
        } else {
            return List.of();
        }
    }

    public List<Variant> selectByMultipart(Map<String, String> state) {
        List<Variant> list = new ArrayList<>();

        for (MultiPartCase multiPartCase : multipart) {
            if (multiPartCase.check(state)) {
                list.addAll(multiPartCase.getApply());
            }
        }

        if (list.isEmpty()) {
            log.warn("No variants found by multipart, state: {}", state);
        }

        return list;
    }

    /**
     * 根据权重选择变体
     */
    public static Variant selectByWeight(List<Variant> variants) {
        if (variants.size() == 1) {
            return variants.getFirst();
        }

        // 计算总权重
        int totalWeight = variants.stream().mapToInt(Variant::getWeight).sum();

        // 随机选择
        Random random = new Random();
        int randomValue = random.nextInt(totalWeight);
        int currentWeight = 0;

        for (Variant variant : variants) {
            currentWeight += variant.getWeight();
            if (randomValue < currentWeight) {
                return variant;
            }
        }

        // 如果权重计算有问题，返回第一个
        return variants.getFirst();
    }

    public List<Variant> getDefault() {
        if (hasVariants()) {
            for (List<Variant> sublist : variants.values()) {
                if (sublist != null && !sublist.isEmpty()) {
                    return sublist;
                }
            }
        }

        if (hasMultipart()) {
            for (MultiPartCase partCase : multipart) {
                List<Variant> sublist = partCase.getApply();
                if (sublist != null && !sublist.isEmpty()) {
                    return sublist;
                }
            }
        }

        log.info("Nothing found in this blockstate");
        return List.of();
    }
}