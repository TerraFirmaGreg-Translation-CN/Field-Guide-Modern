package team.terrafirmgreg.fieldguide.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects registry ids with no generated icon during site build.
 */
public class MissingIconReport {

    private final List<String> missing = new ArrayList<>();

    public void record(String registryId) {
        if (!missing.contains(registryId)) {
            missing.add(registryId);
        }
    }

    public List<String> missing() {
        return Collections.unmodifiableList(missing);
    }

    public boolean isEmpty() {
        return missing.isEmpty();
    }

    public void failIfRequested(boolean failOnMissing) {
        if (failOnMissing && !missing.isEmpty()) {
            throw new IllegalStateException("Missing " + missing.size() + " generated icons: " + missing.subList(0, Math.min(5, missing.size())));
        }
    }
}
