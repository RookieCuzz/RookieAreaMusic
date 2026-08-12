package io.github.rookiecuzz.rookieareamusic.editor;

import io.github.rookiecuzz.rookieareamusic.config.RegionShapeConfig;
import io.github.rookiecuzz.rookieareamusic.geometry.SlicedPolygonVolume;

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

    public enum AddPointResult {
        POINT_ADDED,
        HEIGHT_LOCKED_AND_POINT_ADDED,
        HEIGHT_LOCKED_AND_EXISTING_POLYGON_REPLACED,
        HEIGHT_LOCKED_FOR_COPIED_POLYGON,
        HEIGHT_LOCKED_FOR_EXISTING_POLYGON
    }

    private final Mode mode;
    private final String worldName;
    private final String areaId;
    private final int minY;
    private final int maxY;
    private final NavigableMap<Integer, List<RegionShapeConfig.Point>> slices =
            new TreeMap<>();
    private Integer currentY;
    private Integer pendingAfterY;
    private boolean pendingBlank;
    private boolean heightLockOnly;
    private List<RegionShapeConfig.Point> draft;
    private long cancelConfirmationDeadline;
    private long draftRevision;
    private long validatedDraftRevision = -1L;
    private String cachedValidationError;

    public RegionEditSession(Mode mode,
                             String worldName,
                             String areaId,
                             int minY,
                             int maxY,
                             Integer selectionY,
                             RegionShapeConfig initialShape) {
        if(mode == null){
            throw new IllegalArgumentException("编辑模式不能为空");
        }
        if(isBlank(worldName) || isBlank(areaId)){
            throw new IllegalArgumentException("世界和区域 ID 不能为空");
        }
        if(minY > maxY){
            throw new IllegalArgumentException("世界高度范围无效");
        }
        if(selectionY != null && (selectionY < minY || selectionY > maxY)){
            throw new IllegalArgumentException("切片选择高度超出世界范围");
        }
        this.mode = mode;
        this.worldName = worldName;
        this.areaId = areaId;
        this.minY = minY;
        this.maxY = maxY;

        loadInitialShape(initialShape);
        if(!slices.isEmpty()){
            Integer selected = selectionY == null ? null : slices.floorKey(selectionY);
            this.currentY = selected == null ? slices.firstKey() : selected;
            this.draft = copyPoints(slices.get(this.currentY));
        } else {
            this.currentY = null;
            this.pendingBlank = true;
            this.draft = new ArrayList<>();
        }
    }

    public AddPointResult addPoint(int blockY, double x, double z){
        if(!isFinite(x) || !isFinite(z)){
            throw new IllegalArgumentException("顶点坐标必须是有限数字");
        }
        boolean heightLocked = false;
        boolean replacingExisting = false;
        AddPointResult lockedResult = null;
        if(currentY == null){
            boolean blank = pendingBlank;
            List<RegionShapeConfig.Point> existing = slices.get(blockY);
            lockPendingHeight(blockY);
            heightLocked = true;
            if(blank){
                draft = new ArrayList<>();
                replacingExisting = existing != null;
            } else if(existing != null){
                draft = copyPoints(existing);
                lockedResult = AddPointResult.HEIGHT_LOCKED_FOR_EXISTING_POLYGON;
            } else if(!draft.isEmpty()){
                lockedResult = AddPointResult.HEIGHT_LOCKED_FOR_COPIED_POLYGON;
            }
        }
        if(lockedResult != null){
            heightLockOnly = true;
            invalidateDraftValidation();
            cancelConfirmationDeadline = 0L;
            return lockedResult;
        }
        if(draft.size() >= SlicedPolygonVolume.MAX_VERTICES_PER_SLICE){
            throw new IllegalStateException(
                    "每个切片的顶点数不能超过 "
                            + SlicedPolygonVolume.MAX_VERTICES_PER_SLICE
            );
        }
        draft.add(RegionShapeConfig.Point.builder().x(x).z(z).build());
        heightLockOnly = false;
        invalidateDraftValidation();
        cancelConfirmationDeadline = 0L;
        return heightLocked
                ? (replacingExisting
                        ? AddPointResult.HEIGHT_LOCKED_AND_EXISTING_POLYGON_REPLACED
                        : AddPointResult.HEIGHT_LOCKED_AND_POINT_ADDED)
                : AddPointResult.POINT_ADDED;
    }

    public boolean undoLastPoint(){
        if(heightLockOnly && currentY != null){
            currentY = null;
            restorePendingDraft();
            heightLockOnly = false;
            invalidateDraftValidation();
            cancelConfirmationDeadline = 0L;
            return true;
        }
        if(currentY == null){
            return false;
        }
        if(draft.isEmpty()){
            return false;
        }
        draft.remove(draft.size() - 1);
        unlockUnsavedHeightWhenEmpty();
        invalidateDraftValidation();
        cancelConfirmationDeadline = 0L;
        return true;
    }

    public void clearCurrentSlice(){
        boolean unlockAsBlank = currentY == null
                || (currentY != null && !slices.containsKey(currentY))
                || pendingBlank;
        draft.clear();
        heightLockOnly = false;
        if(unlockAsBlank){
            pendingBlank = true;
        }
        unlockUnsavedHeightWhenEmpty();
        invalidateDraftValidation();
        cancelConfirmationDeadline = 0L;
    }

    public void saveCurrentSlice(){
        if(currentY == null){
            throw new IllegalStateException("请先右键一个方块确定当前切片 Y");
        }
        String error = currentValidationError();
        if(error != null){
            throw new IllegalStateException(error);
        }
        validateSaveCapacity();
        slices.put(currentY, copyPoints(draft));
        pendingAfterY = null;
        pendingBlank = false;
        heightLockOnly = false;
        cancelConfirmationDeadline = 0L;
    }

    public void saveAndNext(boolean blank){
        if(currentY == null){
            throw new IllegalStateException("请先右键一个方块确定当前切片 Y");
        }
        if(currentY >= maxY){
            throw new IllegalStateException("已经到达世界最高可用切片");
        }
        saveCurrentSlice();
        Integer savedY = currentY;
        pendingAfterY = savedY;
        pendingBlank = blank;
        currentY = null;
        draft = blank
                ? new ArrayList<>()
                : copyPoints(slices.get(savedY));
        heightLockOnly = false;
        invalidateDraftValidation();
    }

    public void saveAndPrevious(){
        if(currentY == null){
            if(pendingAfterY == null){
                throw new IllegalStateException("已经是第一张切片");
            }
            currentY = pendingAfterY;
            pendingAfterY = null;
            pendingBlank = false;
            draft = copyPoints(slices.get(currentY));
            heightLockOnly = false;
            invalidateDraftValidation();
            cancelConfirmationDeadline = 0L;
            return;
        }
        Integer previousY = slices.lowerKey(currentY);
        if(previousY == null){
            throw new IllegalStateException("已经是第一张切片");
        }
        saveCurrentSlice();
        currentY = previousY;
        pendingBlank = false;
        draft = copyPoints(slices.get(previousY));
        heightLockOnly = false;
        invalidateDraftValidation();
    }

    public RegionShapeConfig finish(){
        if(currentY == null){
            throw new IllegalStateException(
                    slices.isEmpty()
                            ? "请先右键一个方块确定首张切片 Y"
                            : "下一切片尚未选择 Y，请先点击方块或返回上一层"
            );
        }
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
        if(validatedDraftRevision == draftRevision){
            return cachedValidationError;
        }
        cachedValidationError = validateCurrentDraft();
        validatedDraftRevision = draftRevision;
        return cachedValidationError;
    }

    private String validateCurrentDraft(){
        if(currentY == null){
            return slices.isEmpty()
                    ? "请右键第一个方块确定首张切片 Y"
                    : "请右键一个方块确定下一切片 Y";
        }
        if(draft.size() < 3){
            return "当前切片至少需要 3 个顶点";
        }
        if(draft.size() > SlicedPolygonVolume.MAX_VERTICES_PER_SLICE){
            return "每个切片的顶点数不能超过 "
                    + SlicedPolygonVolume.MAX_VERTICES_PER_SLICE;
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

    public Integer getCurrentY(){
        return currentY;
    }

    public boolean isAwaitingHeight(){
        return currentY == null;
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

    private void validateSaveCapacity(){
        if(currentY == null){
            throw new IllegalStateException("请先右键一个方块确定当前切片 Y");
        }
        boolean newSlice = !slices.containsKey(currentY);
        if(newSlice && slices.size() >= SlicedPolygonVolume.MAX_SLICE_COUNT){
            throw new IllegalStateException(
                    "切片数量不能超过 " + SlicedPolygonVolume.MAX_SLICE_COUNT
            );
        }

        long totalVertices = draft.size();
        for(Map.Entry<Integer, List<RegionShapeConfig.Point>> entry : slices.entrySet()){
            if(!entry.getKey().equals(currentY)){
                totalVertices += entry.getValue().size();
            }
        }
        if(totalVertices > SlicedPolygonVolume.MAX_TOTAL_VERTICES){
            throw new IllegalStateException(
                    "所有切片的顶点总数不能超过 "
                            + SlicedPolygonVolume.MAX_TOTAL_VERTICES
            );
        }
    }

    private void lockPendingHeight(int blockY){
        if(blockY < minY || blockY > maxY){
            throw new IllegalArgumentException("点击方块的 Y 超出世界可用范围: " + blockY);
        }
        if(pendingAfterY != null && blockY <= pendingAfterY){
            throw new IllegalArgumentException(
                    "下一切片 Y 必须高于上一切片 " + pendingAfterY
            );
        }
        boolean existingSlice = slices.containsKey(blockY);
        if(!existingSlice && slices.size() >= SlicedPolygonVolume.MAX_SLICE_COUNT){
            throw new IllegalStateException(
                    "切片数量不能超过 " + SlicedPolygonVolume.MAX_SLICE_COUNT
            );
        }
        currentY = blockY;
    }

    private void unlockUnsavedHeightWhenEmpty(){
        if(draft.isEmpty()
                && currentY != null
                && (!slices.containsKey(currentY) || pendingBlank)){
            currentY = null;
        }
    }

    private void restorePendingDraft(){
        if(pendingBlank || pendingAfterY == null){
            draft = new ArrayList<>();
            return;
        }
        draft = copyPoints(slices.get(pendingAfterY));
    }

    private void invalidateDraftValidation(){
        draftRevision++;
        validatedDraftRevision = -1L;
        cachedValidationError = null;
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
