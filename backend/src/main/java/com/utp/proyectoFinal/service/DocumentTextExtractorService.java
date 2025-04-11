package com.utp.proyectoFinal.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.IOException;

/**
 * Servicio para extraer texto de documentos PDF y Word
 */
@Service
@Slf4j
public class DocumentTextExtractorService {

    /**
     * Extrae texto de un archivo PDF o Word
     * 
     * @param file Archivo a procesar
     * @return Texto extraído del documento
     * @throws IOException Si ocurre un error al procesar el archivo
     */
    public String extractText(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo es nulo o está vacío");
        }
        
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("No se pudo determinar el tipo de archivo");
        }
        
        log.info("Extrayendo texto de archivo: {}, tipo: {}", file.getOriginalFilename(), contentType);
        
        try (InputStream inputStream = file.getInputStream()) {
            if (contentType.equals("application/pdf")) {
                return extractTextFromPdf(inputStream);
            } else if (contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
                return extractTextFromDocx(inputStream);
            } else {
                throw new IllegalArgumentException("Formato de archivo no soportado: " + contentType + ". Solo se admiten PDF y DOCX.");
            }
        }
    }
    
    /**
     * Extrae texto de un archivo PDF
     */
    private String extractTextFromPdf(InputStream inputStream) throws IOException {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }
    
    /**
     * Extrae texto de un archivo Word moderno (DOCX)
     */
    private String extractTextFromDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }
    
    /**
     * Limpia el texto extraído para mejorar su procesamiento
     * 
     * @param text Texto a limpiar
     * @return Texto limpio
     */
    public String cleanExtractedText(String text) {
        if (text == null) {
            return "";
        }
        
        // Eliminar caracteres no imprimibles
        text = text.replaceAll("[\\p{C}]", " ");
        
        // Normalizar espacios en blanco
        text = text.replaceAll("\\s+", " ");
        
        // Eliminar espacios al inicio y final
        text = text.trim();
        
        return text;
    }
}
