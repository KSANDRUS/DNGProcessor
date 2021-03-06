package amirz.dngprocessor.pipeline.post;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.intermediate.MergeDetail;

import static android.opengl.GLES20.GL_TEXTURE0;

public class BlurLCE extends Stage {
    private final ProcessParams mProcessParams;
    private Texture mWeakBlur;
    private Texture mStrongBlur;

    public BlurLCE(ProcessParams process) {
        mProcessParams = process;
    }

    public Texture getWeakBlur() {
        return mWeakBlur;
    }

    public Texture getStrongBlur() {
        return mStrongBlur;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        if (!mProcessParams.lce) {
            return;
        }

        Texture intermediate = previousStages.getStage(MergeDetail.class).getIntermediate();
        GLPrograms converter = getConverter();

        int w = intermediate.getWidth();
        int h = intermediate.getHeight();

        try (Texture tmp = new Texture(w, h, 1, Texture.Format.Float16, null)) {
            // First render to the tmp buffer.
            intermediate.bind(GL_TEXTURE0);
            converter.seti("buf", 0);
            converter.seti("bufSize", w, h);
            converter.setf("sigma", 2f);
            converter.seti("radius", 6);
            converter.seti("dir", 0, 1); // Vertical
            converter.setf("ch", 0, 1); // xy[Y]

            tmp.setFrameBuffer();
            converter.drawBlocks(w, h);

            // Now render from tmp to the real buffer.
            tmp.bind(GL_TEXTURE0);

            converter.seti("dir", 1, 0); // Horizontal
            converter.setf("ch", 1, 0); // [Y]00

            mWeakBlur = new Texture(w, h, 1, Texture.Format.Float16, null);
            mWeakBlur.setFrameBuffer();
            converter.drawBlocks(w, h);

            // Render to tmp again.
            intermediate.bind(GL_TEXTURE0);
            converter.setf("sigma", 3f);
            converter.seti("radius", 9);
            converter.seti("dir", 0, 1); // Vertical
            converter.setf("ch", 0, 1); // xy[Y]

            tmp.setFrameBuffer();
            converter.drawBlocks(w, h);

            // Now render from tmp to the real buffer.
            tmp.bind(GL_TEXTURE0);
            converter.seti("dir", 1, 0); // Horizontal
            converter.setf("ch", 1, 0); // [Y]00

            mStrongBlur = new Texture(w, h, 1, Texture.Format.Float16, null);
            mStrongBlur.setFrameBuffer();
            converter.drawBlocks(w, h);
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage3_0_blur_fs;
    }
}
