package com.maxenonyme.AbyssDimension.entities.parts;

import java.util.List;

public interface HasSegments {
    List<SegmentHitbox> segments();
    List<SegmentDefinition> segmentDefinitions();
    void recreateSegments();
}
