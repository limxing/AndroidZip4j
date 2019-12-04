package me.leefeng.zip4j.tasks;

import me.leefeng.zip4j.exception.ZipException;
import me.leefeng.zip4j.io.inputstream.ZipInputStream;
import me.leefeng.zip4j.model.FileHeader;
import me.leefeng.zip4j.model.LocalFileHeader;
import me.leefeng.zip4j.model.ZipModel;
import me.leefeng.zip4j.progress.ProgressMonitor;
import me.leefeng.zip4j.util.UnzipUtil;
import me.leefeng.zip4j.util.Zip4jUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Matcher;

import static me.leefeng.zip4j.util.InternalZipConstants.BUFF_SIZE;
import static me.leefeng.zip4j.util.InternalZipConstants.FILE_SEPARATOR;

public abstract class AbstractExtractFileTask<T> extends AsyncZipTask<T> {

  private ZipModel zipModel;
  private byte[] buff = new byte[BUFF_SIZE];

  public AbstractExtractFileTask(ProgressMonitor progressMonitor, boolean runInThread, ZipModel zipModel) {
    super(progressMonitor, runInThread);
    this.zipModel = zipModel;
  }

  protected void extractFile(ZipInputStream zipInputStream, FileHeader fileHeader, String outputPath,
                             String newFileName, ProgressMonitor progressMonitor) throws IOException {

    if (!outputPath.endsWith(FILE_SEPARATOR)) {
      outputPath += FILE_SEPARATOR;
    }

    File outputFile = determineOutputFile(fileHeader, outputPath, newFileName);
    progressMonitor.setFileName(outputFile.getAbsolutePath());

    // make sure no file is extracted outside of the target directory (a.k.a zip slip)
    if (!outputFile.getCanonicalPath().startsWith(new File(outputPath).getCanonicalPath())) {
      throw new ZipException("illegal file name that breaks out of the target directory: "
          + fileHeader.getFileName());
    }

    verifyNextEntry(zipInputStream, fileHeader);

    if (fileHeader.isDirectory()) {
      if (!outputFile.exists()) {
        if (!outputFile.mkdirs()) {
          throw new ZipException("Could not create directory: " + outputFile);
        }
      }
    } else {
      checkOutputDirectoryStructure(outputFile);
      unzipFile(zipInputStream, fileHeader, outputFile, progressMonitor);
    }
  }

  private void unzipFile(ZipInputStream inputStream, FileHeader fileHeader, File outputFile,
                         ProgressMonitor progressMonitor) throws IOException {
    int readLength;
    try (OutputStream outputStream = new FileOutputStream(outputFile)) {
      while ((readLength = inputStream.read(buff)) != -1) {
        outputStream.write(buff, 0, readLength);
        progressMonitor.updateWorkCompleted(readLength);
        verifyIfTaskIsCancelled();
      }
    } catch (Exception e) {
      if (outputFile.exists()) {
        outputFile.delete();
      }
      throw  e;
    }

    UnzipUtil.applyFileAttributes(fileHeader, outputFile);
  }

  private void verifyNextEntry(ZipInputStream zipInputStream, FileHeader fileHeader) throws IOException {
    LocalFileHeader localFileHeader = zipInputStream.getNextEntry(fileHeader);

    if (localFileHeader == null) {
      throw new ZipException("Could not read corresponding local file header for file header: "
          + fileHeader.getFileName());
    }

    if (!fileHeader.getFileName().equals(localFileHeader.getFileName())) {
      throw new ZipException("File header and local file header mismatch");
    }
  }

  private void checkOutputDirectoryStructure(File outputFile) throws ZipException {
    if (!outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs()) {
      throw new ZipException("Unable to create parent directories: " + outputFile.getParentFile());
    }
  }

  private File determineOutputFile(FileHeader fileHeader, String outputPath, String newFileName) {
    String outputFileName;
    if (Zip4jUtil.isStringNotNullAndNotEmpty(newFileName)) {
      outputFileName = newFileName;
    } else {
      outputFileName = getFileNameWithSystemFileSeparators(fileHeader.getFileName()); // replace all slashes with file separator
    }

    return new File(outputPath + FILE_SEPARATOR + outputFileName);
  }

  private String getFileNameWithSystemFileSeparators(String fileNameToReplace) {
    return fileNameToReplace.replaceAll("[/\\\\]", Matcher.quoteReplacement(FILE_SEPARATOR));
  }

  @Override
  protected ProgressMonitor.Task getTask() {
    return ProgressMonitor.Task.EXTRACT_ENTRY;
  }

  public ZipModel getZipModel() {
    return zipModel;
  }
}
