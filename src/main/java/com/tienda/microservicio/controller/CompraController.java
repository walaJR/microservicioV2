package com.tienda.microservicio.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.microservicio.model.Boleta;
import com.tienda.microservicio.model.ProductoCompra;
import com.tienda.microservicio.service.CompraService;

@RestController
@RequestMapping("/api/compras")
public class CompraController {

    @Autowired
    private CompraService compraService;

    @PostMapping
    public ResponseEntity<Boleta> realizarCompra(@RequestBody List<ProductoCompra> carrito) {
        Boleta boleta = compraService.procesarCompra(carrito);
        return ResponseEntity.ok(boleta);
    }

    @GetMapping("/{boletaId}/pdf")
    public ResponseEntity<byte[]> descargarBoletaPDF(@PathVariable Long boletaId) {
        try {
            byte[] pdfBytes = compraService.generarPDFBoleta(boletaId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.add("Content-Disposition", "attachment; filename=\"boleta_" + boletaId + ".pdf\"");
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{boletaId}/pdf/download")
    public ResponseEntity<byte[]> forzarDescargaBoletaPDF(@PathVariable Long boletaId) {
        try {
            byte[] pdfBytes = compraService.generarPDFBoleta(boletaId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.add("Content-Disposition", "attachment; filename=\"boleta_" + boletaId + ".pdf\"");
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}