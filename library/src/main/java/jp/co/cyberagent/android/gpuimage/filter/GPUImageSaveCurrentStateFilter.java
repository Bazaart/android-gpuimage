package jp.co.cyberagent.android.gpuimage.filter;

/**
 * filter that serves as a tag to save current state of gpu
 * and normal blend/subtract blend (paste/crop) on top of result later on.
 * @see GPUImageSavedStateTwoInputFilter inheritors for blending modes
 */
public class GPUImageSaveCurrentStateFilter extends GPUImageFilter {
    private final String tag;

    public String getTag() {
        return tag;
    }

    public GPUImageSaveCurrentStateFilter(String tag) {
        this.tag = tag;
    }
}
