package com.railway.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.railway.document.BookingDocument;
import com.railway.document.FoodOrderDocument;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;

@Service
public class PdfTicketService {

    public byte[] ticket(BookingDocument b) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Document doc = new Document(new PdfDocument(new PdfWriter(out)))) {
            doc.add(new Paragraph("E-TICKET").setBold().setFontSize(22));
            doc.add(new Paragraph("PNR: " + b.pnr).setBold());
            doc.add(new Paragraph("Train: " + b.trainNumber + "   Date: " + b.travelDate + "   Class: " + b.travelClass));
            doc.add(new Paragraph("Status: " + b.bookingStatus + "   Amount paid: INR " + b.amount));
            doc.add(new Paragraph("Passengers:").setBold());
            for (int i = 0; i < b.passengers.size(); i++) {
                String seat = i < b.seats.size() ? b.seats.get(i).coach + " / " + b.seats.get(i).seatNumber : "-";
                doc.add(new Paragraph((i + 1) + ". " + b.passengers.get(i).name + " (" + b.passengers.get(i).age + ", "
                        + b.passengers.get(i).gender + ")  Seat: " + seat));
            }
        }
        return out.toByteArray();
    }

    public byte[] invoice(FoodOrderDocument o) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Document doc = new Document(new PdfDocument(new PdfWriter(out)))) {
            doc.add(new Paragraph("FOOD INVOICE").setBold().setFontSize(20));
            doc.add(new Paragraph("Order: " + o.orderId + "   PNR: " + o.pnr + "   Station: " + o.stationCode));
            o.lines.forEach(l -> doc.add(new Paragraph(l.name + " x" + l.qty + "  INR " + (l.price * l.qty))));
            doc.add(new Paragraph("TOTAL: INR " + o.totalAmount).setBold());
        }
        return out.toByteArray();
    }
}
