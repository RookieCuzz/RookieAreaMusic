package com.gitee.niocho.areamusic.editor;

import com.gitee.niocho.areamusic.config.RegionShapeConfig;
import com.gitee.niocho.areamusic.geometry.SlicedPolygonVolume;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Pure state machine for one CT-style region editing session.
 */
public final class RegionEditSession {
    public enum Mode {
        CREATE,
        EDIT
    }

    private final Mode mode;
    private final String worldName;
    private final String areaId;
    private final int minY;
    private final int maxY;
    private final NavigableMap<Integer, List<RegionShapeConfig.Point>> slices =
            new TreeMap<>();
    private int currentY;
    private List<RegionShapeConfig.Point> draft;
    private long cancelConfirmationDeadline;

    public RegionEditSession(Mode mode,
                             String worldName,
                             String areaId,
                             int minY,
                             int maxY,
                             int currentY,
                             RegionShapeConfig initialShape) {
        if(mode == null){
            throw new IllegalArgumentException("编辑模式不能为空");
        }
        if(isBlank(worldName) || isBlank(areaId)){
            throw new IllegalArgumentException("世界和区域 ID 不能为空");
        }
        if(minY > maxY || currentY < minY || currentY > maxY){
            throw new IllegalArgumentException("当前切片高度超出世界范围");
        }
        this.mode = mode;
        this.worldName = worldName;
        this.areaId = areaId;
        this.minY = minY;
        this.maxY = maxY;

        loadInitialShape(initialShape);
        if(!slices.isEmpty()){
            Integer selected = slices.floorKey(currentY);
            this.currentY = selected == null ? slices.firstKey() : selected;
            this.draft = copyPoints(slices.get(this.currentY));
        } else {
            this.currentY = currentY;
            this.draft = new ArrayList<>();
        }
    }

    public void addPoint(double x, double z){
        if(!isFinite(x) || !isFinite(z)){
            throw new IllegalArgumentException("顶点坐标必须是有限数字");
        }
        draft.add(RegionShapeConfig.Point.builder().x(x).z(z).build());
        cancelConfirmationDeadline = 0L;
    }

    public boolean undoLastPoint(){
        if(draft.isEmpty()){
            return false;
        }
        draft.remove(draft.size() - 1);
        cancelConfirmationDeadline = 0L;
        return true;
    }

    public void clearCurrentSlice(){
        draft.clear();
        cancelConfirmationDeadline = 0L;
    }

    public void saveCurrentSlice(){
        String error = currentValidationError();
        if(error != null){
            throw new IllegalStateException(error);
        }
        slices.put(currentY, copyPoints(draft));
        cancelConfirmationDeadline = 0L;
    }

    public void saveAndNext(int playerY, boolean blank){
        saveCurrentSlice();
        int targetY = playerY > currentY ? playerY : currentY + 1;
        if(targetY < minY || targetY > maxY){
            throw new IllegalStateException("下一切片高度超出世界范围");
        }

        List<RegionShapeConfig.Point> existing = slices.get(targetY);
        List<RegionShapeConfig.Point> previous = slices.get(currentY);
        currentY = targetY;
        if(blank){
            draft = new ArrayList<>();
        } else if(existing != null){
            draft = copyPoints(existing);
        } else {
            draft = copyPoints(previous);
        }
    }

    public void saveAndPrevious(){
        saveCurrentSlice();
        Integer previousY = slices.lowerKey(currentY);
        if(previousY == null){
            throw new IllegalStateException("已经是第一张切片");
        }
        currentY = previousY;
        draft = copyPoints(slices.get(previousY));
    }

    public RegionShapeConfig finish(){
        saveCurrentSlice();
        List<RegionShapeConfig.Slice> result = new ArrayList<>();
        for(Map.Entry<Integer, List<RegionShapeConfig.Point>> entry : slices.entrySet()){
            result.add(RegionShapeConfig.Slice.builder()
                    .y(entry.getKey().doubleValue())
                    .polygon(copyPoints(entry.getValue()))
                    .build());
        }
        RegionShapeConfig config = RegionShapeConfig.builder()
                .type("sliced_polygon")
                .slices(result)
                .build();
        // Validate the complete volume, including duplicate heights and every Polygon.
        return new SlicedPolygonVolume(config).getConfig();
    }

    public String currentValidationError(){
        if(draft.size() < 3){
            return "当前切片至少需要 3 个顶点";
        }
        try {
            RegionShapeConfig config = RegionShapeConfig.builder()
                    .slices(Collections.singletonList(
                            RegionShapeConfig.Slice.builder()
                                    .y((double) currentY)
                                    .polygon(copyPoints(draft))
                                    .build()
                    ))
                    .build();
            new SlicedPolygonVolume(config);
            return null;
        } catch (IllegalArgumentException e){
            return e.getMessage();
        }
    }

    public boolean confirmCancel(long nowMillis){
        if(cancelConfirmationDeadline >= nowMillis){
            cancelConfirmationDeadline = 0L;
            return true;
        }
        cancelConfirmationDeadline = nowMillis + 5000L;
        return false;
    }

    public Mode getMode(){
        return mode;
    }

    public String getWorldName(){
        return worldName;
    }

    public String getAreaId(){
        return areaId;
    }

    public int getCurrentY(){
        return currentY;
    }

    public List<RegionShapeConfig.Point> getDraft(){
        return Collections.unmodifiableList(copyPoints(draft));
    }

    public NavigableMap<Integer, List<RegionShapeConfig.Point>> getSavedSlices(){
        NavigableMap<Integer, List<RegionShapeConfig.Point>> result = new TreeMap<>();
        for(Map.Entry<Integer, List<RegionShapeConfig.Point>> entry : slices.entrySet()){
            result.put(entry.getKey(), Collections.unmodifiableList(copyPoints(entry.getValue())));
        }
        return Collections.unmodifiableNavigableMap(result);
    }

    public int getSavedSliceCount(){
        return slices.size();
    }

    private void loadInitialShape(RegionShapeConfig initialShape){
        if(initialShape == null || initialShape.getSlices() == null){
            return;
        }
        // Run the production validator before accepting an existing shape.
        RegionShapeConfig validated = new SlicedPolygonVolume(initialShape).getConfig();
        for(RegionShapeConfig.Slice slice : validated.getSlices()){
            double y = slice.getY();
            int blockY = (int) Math.rint(y);
            if(Math.abs(y - blockY) > 1.0E-7){
                throw new IllegalArgumentException("游戏内编辑器只支持整数 Y 切片: " + y);
            }
            if(blockY < minY || blockY > maxY){
                throw new IllegalArgumentException("已有切片高度超出世界范围: " + blockY);
            }
            if(slices.put(blockY, copyPoints(slice.getPolygon())) != null){
                throw new IllegalArgumentException("切片 Y 坐标不能重复: " + blockY);
            }
        }
    }

    private static List<RegionShapeConfig.Point> copyPoints(
            List<RegionShapeConfig.Point> source){
        List<RegionShapeConfig.Point> copy = new ArrayList<>();
        if(source == null){
            return copy;
        }
        for(RegionShapeConfig.Point point : source){
            copy.add(RegionShapeConfig.Point.builder()
                    .x(point.getX())
                    .z(point.getZ())
                    .build());
        }
        return copy;
    }

    private static boolean isFinite(double value){
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean isBlank(String value){
        return value == null || value.trim().isEmpty();
    }
}
