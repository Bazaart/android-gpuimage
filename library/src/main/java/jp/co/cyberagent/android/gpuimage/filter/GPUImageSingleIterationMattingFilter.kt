package jp.co.cyberagent.android.gpuimage.filter

import android.util.Size


/**
 * implementation a single iteration of multi level matting algorithm
 * @see https://arxiv.org/pdf/2006.14970.pdf]
 * input:
 * image - base image set as the image in GpuImage
 * mask  - index 0 as first input image (beside base)
 * foreground - ongoing result of foreground
 * background - ongoing result of background
 * @param renderFg true for output to be foreground result else background result
 */
class GPUImageSingleIterationMattingFilter(renderFg: Boolean, val size: Size) :
    GPUImageMultipleInputFilter(
        CROSS_TEXTURE_SAMPLING_VERTEX_SHADER,
        MATTING_FRAGMENT_SHADER.format(if (renderFg) "fgResult" else "bgResult")
    ) {


    companion object {
        const val CROSS_TEXTURE_SAMPLING_VERTEX_SHADER = """
        precision mediump float;
        attribute vec4 position;
        attribute vec4 inputTextureCoordinate;
        attribute vec4 inputTextureCoordinate2;
        attribute vec4 inputTextureCoordinate3;
        attribute vec4 inputTextureCoordinate4;
        
        varying vec2 textureCoordinate;
        varying vec2 textureCoordinate2;
        varying vec2 textureCoordinate3;
        varying vec2 textureCoordinate4;
        
        varying vec2 crossCoordinates[4];
        
        void main()
        {
            gl_Position = position;
        
            textureCoordinate = inputTextureCoordinate.xy;
            textureCoordinate2 = inputTextureCoordinate2.xy;
            textureCoordinate3 = inputTextureCoordinate3.xy;
            textureCoordinate4 = inputTextureCoordinate4.xy;
            
            crossCoordinates[0] = vec2(1.0,0.0);
            crossCoordinates[1] = vec2(0.0,1.0);
            crossCoordinates[2] = vec2(-1.0,0.0);
            crossCoordinates[3] = vec2(0.0,-1.0);

        }
        """

        const val MATTING_FRAGMENT_SHADER = """
            precision mediump float;
            varying highp vec2 textureCoordinate;
            varying highp vec2 textureCoordinate2;
            varying highp vec2 textureCoordinate3;
            varying highp vec2 textureCoordinate4;
            
            uniform sampler2D inputImageTexture;
            uniform sampler2D inputImageTexture2;
            uniform sampler2D inputImageTexture3;
            uniform sampler2D inputImageTexture4;
            
            varying highp vec2 crossCoordinates[4];
            
            void main() 
            {
                lowp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);
                lowp vec4 textureColor2 = texture2D(inputImageTexture2, textureCoordinate2);
                
                highp float a0 = textureColor2.a;
                highp float a1 = 1.0 - a0;
                
                highp float a00 = a0 * a0;
                highp float a01 = a0 * a1;
                highp float a11 = a1 * a1;
                
                lowp vec3 fg = textureColor.rgb * a0;
                lowp vec3 bg = textureColor.rgb * a1;
               
                
                //start for each pixel in cross
                for (int i = 0; i < 4; i++)
                {
                    lowp vec2 cord = textureCoordinate + crossCoordinates[i];
                    lowp vec4 alpha = texture2D(inputImageTexture2, cord);
                    
                    lowp float da = 0.00001 + abs(a0 - alpha.a);
                    a00 = a00 + da;
                    a11 = a11 + da;
                    
                    lowp vec4 fgValue = texture2D(inputImageTexture3, cord);
                    lowp vec4 bgValue = texture2D(inputImageTexture4, cord);
                    
                    fg = fg + da * fgValue.rgb;
                    bg = bg + da * bgValue.rgb;
                }
                
                highp float determinant = a00 * a11 - a01 * a01;
                highp float invDet = 1.0/determinant;
                
                highp float b00 = invDet * a11;
                highp float b01 = invDet * -a01;
                highp float b11 = invDet * a00;
                
                lowp vec3 fc = b00 * fg + b01 * bg;
                lowp vec3 bc = b01 * fg + b11 * bg;
                
                lowp vec4 fgResult = vec4(max(0.0, min(1.0, fc.r)), max(0.0, min(1.0, fc.g)),max(0.0, min(1.0, fc.b)), textureColor2.a);
                lowp vec4 bgResult = vec4(max(0.0, min(1.0, bc.r)), max(0.0, min(1.0, bc.g)),max(0.0, min(1.0, bc.b)), 1.0-textureColor2.a);
                
                gl_FragColor = %s;
            }
        """
    }

}