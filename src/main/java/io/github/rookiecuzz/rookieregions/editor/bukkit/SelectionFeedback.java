package io.github.rookiecuzz.rookieregions.editor.bukkit;

/** Pure selection result rendered by the Bukkit listener. */
public record SelectionFeedback(String message) {
    public SelectionFeedback {
        if(message == null || message.isBlank()){
            throw new IllegalArgumentException("selection message cannot be blank");
        }
    }
}
