package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ClientHandler {
    private final Object lock;
    private Command runningCommand;
    private boolean shouldTerminate;
    private InputStream inputStream;
    private FileOutputStream fileOutputStream;
    private OutputStream out;
    private File workingFile;
    
    public ClientHandler(OutputStream out) {
        this.out = out;
        this.lock = new Object();
        this.runningCommand = null;
        this.shouldTerminate = false;
        this.inputStream = null;
        this.fileOutputStream = null;
    }

    public void setRunningCommand(Command command) {
        runningCommand = command;
    }

    public void setWorkingFile(File file) {
        workingFile = file;
    }

    public File getWorkingFile() {
        return workingFile;
    }

    public Command getRunningCommand() {
        return runningCommand;
    }

    public void setShouldTerminate(boolean shouldTerminate) {
        this.shouldTerminate = shouldTerminate;
    }

    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    public Object getLock() {
        return lock;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public void setFileInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public FileOutputStream getFileOutputStream() {
        return fileOutputStream;
    }

    public void setFileOutputStream(FileOutputStream fileOutputStream) {
        this.fileOutputStream = fileOutputStream;
    }

    public synchronized void writeToOutputStream(byte[] bytes) {
        try {
            out.write(bytes);
            out.flush();
        } catch (Exception e) {
            System.out.println("Error writing to output stream");
        }
    }

}
