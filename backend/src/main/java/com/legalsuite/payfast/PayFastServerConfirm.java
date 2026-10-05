package com.legalsuite.payfast;

import java.util.List;

/** Posts an ITN back to PayFast. Tests supply a fake so CI does not open a socket. */
public interface PayFastServerConfirm {
    boolean confirmed(List<PayFastSignature.Field> fieldsInOrder);
}
