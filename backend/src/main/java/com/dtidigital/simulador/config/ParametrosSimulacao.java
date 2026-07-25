package com.dtidigital.simulador.config;

//parametros fixos simulando 
public final class ParametrosSimulacao {

    public static final double BASE_X = 0.0;
    public static final double BASE_Y = 0.0;

    public static final double VELOCIDADE_KM_H = 30.0;

    public static final double TEMPO_CARREGAMENTO_MINUTOS = 5.0;

    public static final double TEMPO_ENTREGA_MINUTOS = 2.0;

    private ParametrosSimulacao() {
    }

    public static double minutosParaPercorrer(double distanciaKm) {
        return distanciaKm / VELOCIDADE_KM_H * 60.0;
    }

    public static double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}