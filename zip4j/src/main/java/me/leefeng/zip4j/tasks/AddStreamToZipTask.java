package me.leefeng.zip4j.tasks;

import me.leefeng.zip4j.exception.ZipException;
import me.leefeng.zip4j.headers.HeaderUtil;
import me.leefeng.zip4j.headers.HeaderWriter;
import me.leefeng.zip4j.io.outputstream.SplitOutputStream;
import me.leefeng.zip4j.io.outputstream.ZipOutputStream;
import me.leefeng.zip4j.model.FileHeader;
import me.leefeng.zip4j.model.ZipModel;
import me.leefeng.zip4j.model.ZipParameters;
import me.leefeng.zip4j.model.enums.CompressionMethod;
import me.leefeng.zip4j.progress.ProgressMonitor;
import me.leefeng.zip4j.tasks.AddStreamToZipTask.AddStreamToZipTaskParameters;
import me.leefeng.zip4j.tasks.RemoveEntryFromZipFileTask.RemoveEntryFromZipFileTaskParameters;
import me.leefeng.zip4j.util.Zip4jUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import static me.leefeng.zip4j.util.InternalZipConstants.BUFF_SIZE;

public class AddStreamToZipTask extends AbstractAddFileToZipTask<AddStreamToZipTask.AddStreamToZipTaskParameters> {

  public AddStreamToZipTask(ProgressMonitor progressMonitor, boolean runInThread, ZipModel zipModel, char[] password,
                            HeaderWriter headerWriter) {
    super(progressMonitor, runInThread, zipModel, password, headerWriter);
  }

  @Override
  protected void executeTask(AddStreamToZipTaskParameters taskParameters, ProgressMonitor progressMonitor)
      throws IOException {

    verifyZipParameters(taskParameters.zipParameters);

    if (!Zip4jUtil.isStringNotNullAndNotEmpty(taskParameters.zipParameters.getFileNameInZip())) {
      throw new ZipException("fileNameInZip has to be set in zipParameters when adding stream");
    }

    removeFileIfExists(getZipModel(), taskParameters.charset, taskParameters.zipParameters.getFileNameInZip(), progressMonitor);

    // For streams, it is necessary to write extended local file header because of Zip standard encryption.
    // If we do not write extended local file header, zip standard encryption needs a crc upfront for key,
    // which cannot be calculated until we read the complete stream. If we use extended local file header,
    // last modified file time is used, or current system time if not available.
    taskParameters.zipParameters.setWriteExtendedLocalFileHeader(true);

    if (taskParameters.zipParameters.getCompressionMethod().equals(CompressionMethod.STORE)) {
      // Set some random value here. This will be updated again when closing entry
      taskParameters.zipParameters.setEntrySize(0);
    }

    try(SplitOutputStream splitOutputStream = new SplitOutputStream(getZipModel().getZipFile(), getZipModel().getSplitLength());
        ZipOutputStream zipOutputStream = initializeOutputStream(splitOutputStream, taskParameters.charset)) {

      byte[] readBuff = new byte[BUFF_SIZE];
      int readLen = -1;

      ZipParameters zipParameters = taskParameters.zipParameters;
      zipOutputStream.putNextEntry(zipParameters);

      if (!zipParameters.getFileNameInZip().endsWith("/") &&
          !zipParameters.getFileNameInZip().endsWith("\\")) {
        while ((readLen = taskParameters.inputStream.read(readBuff)) != -1) {
          zipOutputStream.write(readBuff, 0, readLen);
        }
      }

      FileHeader fileHeader = zipOutputStream.closeEntry();

      if (fileHeader.getCompressionMethod().equals(CompressionMethod.STORE)) {
        updateLocalFileHeader(fileHeader, splitOutputStream);
      }
    }
  }

  @Override
  protected long calculateTotalWork(AddStreamToZipTaskParameters taskParameters) {
    return 0;
  }

  private void removeFileIfExists(ZipModel zipModel, Charset charset, String fileNameInZip, ProgressMonitor progressMonitor)
      throws ZipException {

    FileHeader fileHeader = HeaderUtil.getFileHeader(zipModel, fileNameInZip);
    if (fileHeader  != null) {
      RemoveEntryFromZipFileTask removeEntryFromZipFileTask = new RemoveEntryFromZipFileTask(progressMonitor, false,
          zipModel);
      removeEntryFromZipFileTask.execute(new RemoveEntryFromZipFileTaskParameters(fileHeader, charset));
    }
  }

  public static class AddStreamToZipTaskParameters extends AbstractZipTaskParameters {
    private InputStream inputStream;
    private ZipParameters zipParameters;

    public AddStreamToZipTaskParameters(InputStream inputStream, ZipParameters zipParameters, Charset charset) {
      super(charset);
      this.inputStream = inputStream;
      this.zipParameters = zipParameters;
    }
  }
}
