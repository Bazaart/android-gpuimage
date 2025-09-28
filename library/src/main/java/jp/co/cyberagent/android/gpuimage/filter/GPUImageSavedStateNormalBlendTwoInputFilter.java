package jp.co.cyberagent.android.gpuimage.filter;

/**
 * a filter that inherits from [GPUImageTwoInputSavedStateFilter]
 * and apply (normal blend/paste) saved state on top of the current result
 * fragment shader is the same as [GPUImageNormalBlendFilter]
 *
 * @see GPUImageSaveCurrentStateFilter
 */
public class GPUImageSavedStateNormalBlendTwoInputFilter extends GPUImageSavedStateTwoInputFilter {

    static private final String FRAGMENT_SHADER = """
            varying highp vec2 textureCoordinate;
            varying highp vec2 textureCoordinate2;
            
            uniform sampler2D inputImageTexture;
            uniform sampler2D inputImageTexture2;
            
            void main()
            {
                lowp vec4 c2 = texture2D(inputImageTexture, textureCoordinate);
                lowp vec4 c1 = texture2D(inputImageTexture2, textureCoordinate2);
            
                lowp vec4 outputColor;
            
                outputColor.r = c1.r * c1.a + c2.r * c2.a * (1.0 - c1.a);
                outputColor.g = c1.g * c1.a + c2.g * c2.a * (1.0 - c1.a);
                outputColor.b = c1.b * c1.a + c2.b * c2.a * (1.0 - c1.a);
                outputColor.a = c1.a + c2.a * (1.0 - c1.a);
            
                outputColor.rgb /= outputColor.a;
                gl_FragColor = vec4(outputColor.rgb, outputColor.a);
            }
            """;

    public GPUImageSavedStateNormalBlendTwoInputFilter(String tag) {
        super(tag, FRAGMENT_SHADER);
        this.tag = tag;
    }
}
