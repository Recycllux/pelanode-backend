package com.recycllux.pelanode.pack;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycllux.pelanode.config.PelanodeProperties;
import com.recycllux.pelanode.domain.ContextRecord;
import com.recycllux.pelanode.domain.DatasetSource;
import com.recycllux.pelanode.domain.DeviceStatus;
import com.recycllux.pelanode.domain.MediaAsset;
import com.recycllux.pelanode.domain.Mission;
import com.recycllux.pelanode.domain.ModelOutput;
import com.recycllux.pelanode.domain.Node;
import com.recycllux.pelanode.domain.Observation;
import com.recycllux.pelanode.domain.TrackPoint;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import com.recycllux.pelanode.config.DataPaths;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DemoPack {

    private final ObjectMapper mapper;
    private final PelanodeProperties props;

    public Path dir() {
        return DataPaths.resolve(props.demoPackDir());
    }

    public Mission mission() {
        return read("mission.json", new TypeReference<>() {});
    }

    public Node node() {
        return read("node.json", new TypeReference<>() {});
    }

    public List<MediaAsset> media() {
        return read("media.json", new TypeReference<>() {});
    }

    public List<DatasetSource> datasets() {
        return readOptional("context/datasets.json", new TypeReference<>() {});
    }

    public List<ContextRecord> contextRecords() {
        return readOptional("context/records.json", new TypeReference<>() {});
    }

    public List<TrackPoint> track() {
        return read("track.json", new TypeReference<>() {});
    }

    public List<Observation> observations() {
        return read("observations.json", new TypeReference<>() {});
    }

    public List<DeviceStatus> deviceStatus() {
        return readOptional("device_status.json", new TypeReference<>() {});
    }

    public List<ModelOutput> modelOutputs() {
        if (props.aiOutputsFile() != null && !props.aiOutputsFile().isBlank()) {
            return readPath(DataPaths.resolve(props.aiOutputsFile()), new TypeReference<>() {});
        }
        return read("inference/model_outputs.json", new TypeReference<>() {});
    }

    private <T> T read(String name, TypeReference<T> type) {
        return readPath(dir().resolve(name), type);
    }

    private <T> List<T> readOptional(String name, TypeReference<List<T>> type) {
        Path path = dir().resolve(name);
        return Files.exists(path) ? readPath(path, type) : List.of();
    }

    private <T> T readPath(Path path, TypeReference<T> type) {
        try {
            return mapper.readValue(path.toFile(), type);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read demo pack file " + path.toAbsolutePath(), e);
        }
    }
}
