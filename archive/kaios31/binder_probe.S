/* ARM EABI static probe. Opens /dev/binder, reads BINDER_VERSION, closes it.
 * No Binder transactions, service registration, filesystem changes or reboot.
 */
.syntax unified
.arm
.global _start
.section .text
_start:
    ldr r0, =device
    mov r1, #2
    mov r2, #0
    mov r7, #5
    svc #0
    cmp r0, #0
    blt failed
    mov r4, r0
    ldr r1, =0xc0046209
    ldr r2, =protocol
    mov r7, #54
    svc #0
    mov r5, r0
    mov r0, r4
    mov r7, #6
    svc #0
    cmp r5, #0
    blt failed
    ldr r0, =protocol
    ldr r0, [r0]
    cmp r0, #7
    beq version7
    cmp r0, #8
    beq version8
    ldr r1, =unknown
    mov r2, #unknown_len
    b emit
version7:
    ldr r1, =msg7
    mov r2, #msg7_len
    b emit
version8:
    ldr r1, =msg8
    mov r2, #msg8_len
emit:
    mov r0, #1
    mov r7, #4
    svc #0
    mov r0, #0
    b finish
failed:
    ldr r1, =error
    mov r2, #error_len
    mov r0, #2
    mov r7, #4
    svc #0
    mov r0, #1
finish:
    mov r7, #1
    svc #0
.section .rodata
device: .asciz "/dev/binder"
msg7: .ascii "Binder protocol 7 (32-bit IPC)\n"
.equ msg7_len, .-msg7
msg8: .ascii "Binder protocol 8 (64-bit IPC)\n"
.equ msg8_len, .-msg8
unknown: .ascii "Unexpected Binder protocol\n"
.equ unknown_len, .-unknown
error: .ascii "Binder probe failed\n"
.equ error_len, .-error
.section .bss
.align 2
protocol: .space 4
.section .note.GNU-stack,"",%progbits
