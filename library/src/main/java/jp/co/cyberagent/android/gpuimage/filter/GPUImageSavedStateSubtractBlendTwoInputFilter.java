package jp.co.cyberagent.android.gpuimage.filter;

/**
 * a filter that inherits from [GPUImageTwoInputSavedStateFilter]
 * and apply (subtract from GPUImageSubtractBlendFilter) saved state on top of the current result
 *
 * @see GPUImageSaveCurrentStateFilter
 */
public class GPUImageSavedStateSubtractBlendTwoInputFilter extends GPUImageSavedStateTwoInputFilter {
    protected String tag;

    public String getTag() {
        return tag;
    }

    public static final String SUBTRACT_BLEND_FRAGMENT_SHADER = "varying highp vec2 textureCoordinate;\n" +
            " varying highp vec2 textureCoordinate2;\n" +
            "\n" +
            " uniform sampler2D inputImageTexture;\n" +
            " uniform sampler2D inputImageTexture2;\n" +
            " \n" +
            " void main()\n" +
            " {\n" +
            "   lowp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "   lowp vec4 textureColor2 = texture2D(inputImageTexture2, textureCoordinate2);\n" +
            "\n" +
            "   gl_FragColor = vec4(textureColor.rgb - textureColor2.rgb, textureColor.a - textureColor2.a);\n" +
            " }";

    public GPUImageSavedStateSubtractBlendTwoInputFilter(String tag) {
        super(tag, SUBTRACT_BLEND_FRAGMENT_SHADER);
        this.tag = tag;
    }
}
