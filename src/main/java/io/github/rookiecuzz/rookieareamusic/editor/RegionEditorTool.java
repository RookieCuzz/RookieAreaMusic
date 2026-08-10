package io.github.rookiecuzz.rookieareamusic.editor;

import org.bukkit.Material;

public enum RegionEditorTool {
    POINT("point", Material.BLAZE_ROD, "§aROI 勾画笔"),
    NEXT("next", Material.LIME_DYE, "§e保存并进入下一层"),
    PREVIOUS("previous", Material.CLOCK, "§b上一层"),
    FINISH("finish", Material.EMERALD, "§a完成编辑"),
    CANCEL("cancel", Material.BARRIER, "§c取消编辑");

    private final String id;
    private final Material material;
    private final String displayName;

    RegionEditorTool(String id, Material material, String displayName) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
    }

    public String getId(){
        return id;
    }

    public Material getMaterial(){
        return material;
    }

    public String getDisplayName(){
        return displayName;
    }

    public static RegionEditorTool fromId(String id){
        if(id == null){
            return null;
        }
        for(RegionEditorTool tool : values()){
            if(tool.id.equals(id)){
                return tool;
            }
        }
        return null;
    }
}
