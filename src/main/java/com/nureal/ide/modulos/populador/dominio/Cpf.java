package com.nureal.ide.modulos.populador.dominio;

import java.util.Random;

/** Gera um CPF com digitos verificadores VALIDOS (formato "000.000.000-00") — plausivel de verdade, nao so 11 digitos aleatorios. */
final class Cpf {

    private Cpf() {
    }

    static String gerar(Random rnd) {
        int[] n = new int[9];
        for (int i = 0; i < 9; i++) {
            n[i] = rnd.nextInt(10);
        }
        int d1 = digitoVerificador(n, 10);
        int d2 = digitoVerificador(appendDigit(n, d1), 11);
        return String.format("%d%d%d.%d%d%d.%d%d%d-%d%d", n[0], n[1], n[2], n[3], n[4], n[5], n[6], n[7], n[8], d1,
                d2);
    }

    private static int[] appendDigit(int[] base, int digit) {
        int[] out = new int[base.length + 1];
        System.arraycopy(base, 0, out, 0, base.length);
        out[base.length] = digit;
        return out;
    }

    private static int digitoVerificador(int[] digitos, int pesoInicial) {
        int soma = 0;
        int peso = pesoInicial;
        for (int d : digitos) {
            soma += d * peso;
            peso--;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
