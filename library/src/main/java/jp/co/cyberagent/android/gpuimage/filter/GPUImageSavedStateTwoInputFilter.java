package jp.co.cyberagent.android.gpuimage.filter;

abstract class GPUImageSavedStateTwoInputFilter extends GPUImageTwoInputFilter {
    protected String tag;

    public String getTag() {
        return tag;
    }

    public GPUImageSavedStateTwoInputFilter(String tag, String fragmentShader) {
        super(fragmentShader);
        this.tag = tag;
    }
}
