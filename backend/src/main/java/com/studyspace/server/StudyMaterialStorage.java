package com.studyspace.server;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
class StudyMaterialStorage {
    private static final long MAX_UNPACKED_BYTES=100L*1024*1024;
    final Path root;
    StudyMaterialStorage(@Value("${studyspace.upload-dir:uploads}") String directory) {
        root=Path.of(directory).toAbsolutePath().normalize();
    }

    record Stored(String filename,String originalName,long size) {}

    Stored store(MultipartFile upload) throws IOException {
        if(upload==null||upload.isEmpty())throw new IllegalArgumentException("Chọn một file tài liệu trước khi tải lên.");
        if(upload.getSize()>25L*1024*1024)throw new IllegalArgumentException("File tối đa 25 MB.");
        String original=Path.of(Objects.requireNonNullElse(upload.getOriginalFilename(),"tai-lieu")).getFileName().toString();
        int dot=original.lastIndexOf('.');
        if(dot<1)throw new IllegalArgumentException("Chỉ nhận PDF, PPT hoặc PPTX.");
        String extension=original.substring(dot+1).toLowerCase(Locale.ROOT);
        byte[] bytes=upload.getBytes();
        if(!valid(extension,bytes))throw new IllegalArgumentException("Định dạng hoặc chữ ký file không hợp lệ.");
        Files.createDirectories(root);
        String filename=UUID.randomUUID()+"."+extension;
        Files.write(root.resolve(filename),bytes,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        String safeName=original.replaceAll("[\\r\\n\\\"]","_");
        return new Stored(filename,safeName,bytes.length);
    }

    private boolean valid(String extension,byte[] bytes) {
        if(extension.equals("pdf"))return bytes.length>=8&&new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
        if(extension.equals("ppt"))return bytes.length>=8&&Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)0xD0,(byte)0xCF,0x11,(byte)0xE0,(byte)0xA1,(byte)0xB1,0x1A,(byte)0xE1});
        return extension.equals("pptx")&&validPowerPointZip(bytes);
    }

    private boolean validPowerPointZip(byte[] bytes) {
        if(bytes.length<4||bytes[0]!='P'||bytes[1]!='K')return false;
        boolean contentTypes=false,presentation=false;
        long expanded=0;int entries=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            var entry=zip.getNextEntry();byte[] buffer=new byte[8192];
            while(entry!=null) {
                if(++entries>20000)return false;
                contentTypes|=entry.getName().equals("[Content_Types].xml");
                presentation|=entry.getName().equals("ppt/presentation.xml");
                int read;while((read=zip.read(buffer))!=-1)if((expanded+=read)>MAX_UNPACKED_BYTES)return false;
                entry=zip.getNextEntry();
            }
        } catch(IOException e) { return false; }
        return contentTypes&&presentation;
    }
}
