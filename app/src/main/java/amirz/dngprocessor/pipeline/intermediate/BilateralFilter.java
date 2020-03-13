package amirz.dngprocessor.pipeline.intermediate;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.ToIntermediate;

import static android.opengl.GLES20.*;

public class BilateralFilter extends Stage {
    private final ProcessParams mProcess;

    private Texture mBlurred, mAHEMap, mDownscaled, mBilateral1, mBilateral2;

    public BilateralFilter(ProcessParams process) {
        mProcess = process;
    }

    public Texture getBlurred() {
        return mBlurred;
    }

    public Texture getAHEMap() {
        return mAHEMap;
    }

    public Texture getBilateral() {
        return mBilateral2;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        super.execute(previousStages);
        GLPrograms converter = getConverter();

        boolean lce = mProcess.lce;
        boolean ahe = mProcess.ahe;

        lce = false;
        ahe = false;

        Texture intermediate = previousStages.getStage(ToIntermediate.class).getIntermediate();
        int w = intermediate.getWidth();
        int h = intermediate.getHeight();

        converter.useProgram(R.raw.helper_extract_channel_fs);
        mBilateral1 = new Texture(w, h, 1, Texture.Format.Float16, null);
        mBilateral2 = new Texture(w, h, 1, Texture.Format.Float16, null);
        intermediate.bind(GL_TEXTURE0);
        converter.seti("buf", 0);
        converter.setf("mult", 0, 0, 1, 0);
        mBilateral1.setFrameBuffer();
        converter.drawBlocks(w, h);

        // Pre-bilateral median filter.
        converter.useProgram(R.raw.stage2_2_median_fs);
        mBilateral1.bind(GL_TEXTURE0);
        converter.seti("buf", 0);
        mBilateral2.setFrameBuffer();
        converter.drawBlocks(w, h);

        // Bilateral filter setup.
        converter.useProgram(R.raw.stage3_0_fs);
        intermediate.bind(GL_TEXTURE2);
        converter.seti("buf", 0);
        converter.seti("bufSize", w, h);
        converter.seti("intermediate", 2);

        // 1) Small area, strong blur.
        mBilateral2.bind(GL_TEXTURE0);
        mBilateral1.setFrameBuffer();
        converter.setf("sigma", 0.3f, 3.f);
        converter.seti("radius", 7, 1);
        converter.drawBlocks(w, h);

        // 2) Medium area, medium blur.
        mBilateral1.bind(GL_TEXTURE0);
        mBilateral2.setFrameBuffer();
        converter.setf("sigma", 0.2f, 9.f);
        converter.seti("radius", 21, 3);
        converter.drawBlocks(w, h);

        // 3) Large area, weak blur.
        mBilateral2.bind(GL_TEXTURE0);
        mBilateral1.setFrameBuffer();
        converter.setf("sigma", 0.1f, 27.f);
        converter.seti("radius", 63, 9);
        converter.drawBlocks(w, h);

        /*
        PreProcess preProcess = previousStages.getStage(PreProcess.class);

        converter.seti("buf", 0);
        converter.seti("bufSize", w, h);
        mBilateralFiltered = new Texture(preProcess.getInWidth(), preProcess.getInHeight(),
                3, Texture.Format.Float16, null);

        // Two iterations means four total filters.
        for (int i = 0; i < 2; i++) {
            intermediate.bind(GL_TEXTURE0);
            mBilateralFiltered.setFrameBuffer();

            //converter.drawBlocks(w, h);

            mBilateralFiltered.bind(GL_TEXTURE0);
            intermediate.setFrameBuffer();

            //converter.drawBlocks(w, h);
        }*/

        converter.useProgram(R.raw.helper_downscale_fs);

        int scale = 2;
        intermediate.bind(GL_TEXTURE0);
        converter.seti("buf", 0);
        converter.seti("scale", scale);

        mDownscaled = new Texture(w / scale, h / scale, 1, Texture.Format.Float16, null, GL_LINEAR);
        mDownscaled.setFrameBuffer();
        //converter.draw();

        // LCE
        if (lce) {
            converter.useProgram(R.raw.stage2_2_fs);

            intermediate.bind(GL_TEXTURE0);
            converter.seti("sampleBuf", 0);
            converter.seti("blurBuf", 0);
            converter.seti("bufSize", w, h);

            converter.seti("dir", 1, 0); // Right
            converter.setf("ch", 0, 1); // xy[Y]

            Texture temp = new Texture(w, h, 1, Texture.Format.Float16, null);
            temp.setFrameBuffer();
            converter.drawBlocks(w, h);

            temp.bind(GL_TEXTURE0);
            converter.seti("dir", 0, 1); // Down
            converter.setf("ch", 1, 0); // [Y]00

            mBlurred = new Texture(w, h, 1, Texture.Format.Float16, null);
            mBlurred.setFrameBuffer();
            converter.drawBlocks(w, h);

            temp.close();
        }

        // AHE
        if (ahe) {
            converter.useProgram(R.raw.stage2_3_fs);

            intermediate.bind(GL_TEXTURE0);
            converter.seti("buf", 0);
            converter.seti("bufSize", w, h);
            converter.seti("mode", 0);
            converter.seti("dir", 1, 0); // Right

            Texture temp = new Texture(w / 2, h / 2, 4, Texture.Format.Float16, null);
            temp.setFrameBuffer();
            converter.drawBlocks(w / 2, h / 2);

            temp.bind(GL_TEXTURE0);
            converter.seti("bufSize", w / 2, h / 2);
            converter.seti("mode", 1);
            converter.seti("dir", 0, 1); // Down

            mAHEMap = new Texture(w / 2, h / 2, 4, Texture.Format.Float16, null, GL_LINEAR);
            mAHEMap.setFrameBuffer();
            converter.drawBlocks(w / 2, h / 2);

            converter.useProgram(R.raw.stage2_4_fs);

            mAHEMap.bind(GL_TEXTURE0);
            converter.seti("buf", 0);
            converter.seti("bufSize", w / 2, h / 2);
            converter.seti("mode", 0);
            converter.seti("dir", 1, 0); // Right

            temp.setFrameBuffer();
            converter.drawBlocks(w / 2, h / 2);

            temp.bind(GL_TEXTURE0);
            converter.seti("mode", 1);
            converter.seti("dir", 0, 1); // Down

            mAHEMap.setFrameBuffer();
            converter.drawBlocks(w / 2, h / 2);

            temp.close();
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage2_2_fs;
    }
}
