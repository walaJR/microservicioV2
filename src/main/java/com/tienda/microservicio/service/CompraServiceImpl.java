package com.tienda.microservicio.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tienda.microservicio.model.Boleta;
import com.tienda.microservicio.model.DetalleBoleta;
import com.tienda.microservicio.model.Producto;
import com.tienda.microservicio.model.ProductoCompra;
import com.tienda.microservicio.repository.BoletaRepository;
import com.tienda.microservicio.repository.ProductoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompraServiceImpl implements CompraService {

    @Autowired
    private ProductoRepository productoRepo;

    @Autowired
    private BoletaRepository boletaRepo;

    @Autowired
    private PDFService pdfService;

    @Override
    public Boleta procesarCompra(List<ProductoCompra> carrito) {

        Boleta boleta = new Boleta();
        boleta.setFecha(LocalDateTime.now());
        List<DetalleBoleta> detalles = new ArrayList<>();

        double total = 0;
        for (ProductoCompra item : carrito) {
            Producto producto = productoRepo.findById(item.getIdProducto())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

            DetalleBoleta detalle = new DetalleBoleta();
            detalle.setProducto(producto);
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(producto.getPrecio());
            detalle.setBoleta(boleta);

            detalles.add(detalle);
            total += producto.getPrecio() * item.getCantidad();
        }

        boleta.setDetalles(detalles);
        boleta.setTotal(total);

        return boletaRepo.save(boleta);
    }

    @Override
    public byte[] generarPDFBoleta(Long boletaId) {
        Boleta boleta = boletaRepo.findById(boletaId)
                .orElseThrow(() -> new RuntimeException("Boleta no encontrada"));
        return pdfService.generarBoletaPDF(boleta);
    }
}