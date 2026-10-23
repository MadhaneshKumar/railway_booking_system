package com.railway.service;

import com.railway.document.BookingDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class TicketFulfillmentService {
    private static final Logger log = LoggerFactory.getLogger(TicketFulfillmentService.class);
    private final PdfTicketService pdf;
    private final BlobStorageService blobs;
    private final NotificationService notifier;
    private final MongoTemplate mongo;

    public TicketFulfillmentService(PdfTicketService pdf, BlobStorageService blobs, NotificationService notifier, MongoTemplate mongo) {
        this.pdf = pdf; this.blobs = blobs; this.notifier = notifier; this.mongo = mongo;
    }

    /** Generates the PDF, stores it in Blob Storage, and sends SMS + email. Never throws (a webhook must still return 200). */
    public void fulfill(BookingDocument b, String subject) {
        try {
            String blobName = "tickets/" + b.pnr + ".pdf";
            blobs.upload(blobName, pdf.ticket(b));
            b.pdfBlobName = blobName;
            mongo.updateFirst(Query.query(Criteria.where("pnr").is(b.pnr)), new Update().set("pdfBlobName", blobName), BookingDocument.class);
            notifier.notify(b.userId, subject, "PNR " + b.pnr + " | Train " + b.trainNumber + " | " + b.travelDate
                    + " | Class " + b.travelClass + " | Status " + b.bookingStatus);
        } catch (Exception e) {
            log.error("Fulfillment failed for {}: {}", b.pnr, e.getMessage(), e);
        }
    }
}
