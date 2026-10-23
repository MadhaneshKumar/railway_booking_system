package com.railway.service;

public class BookingException extends RuntimeException {
    public final String code;
    public BookingException(String code, String message) { super(message); this.code = code; }
}
