package com.maxenonyme.AbyssDimension.entities.parts;

public record SegmentDefinition(
    String name,
    double forwardOffset,
    double verticalOffset,
    double lateralOffset,
    double width,
    double height,
    double length
) {
    public SegmentDefinition(String name, double forwardOffset, double verticalOffset, double lateralOffset, double width, double height) {
        this(name, forwardOffset, verticalOffset, lateralOffset, width, height, width);
    }
}
