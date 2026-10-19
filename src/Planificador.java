import java.util.*;

/**
 * Algoritmos de planificación de procesos: FIFO, SJF, PRIORIDAD y Round Robin (RR).
 *
 * - FIFO (FCFS): atiende los procesos en orden de llegada. No expropiativo.
 * - SJF: entre los procesos que ya llegaron, ejecuta el de menor ráfaga. No expropiativo.
 * - PRIORIDAD: entre los que ya llegaron, ejecuta el de mayor prioridad
 *   (número MENOR = prioridad MÁS ALTA). No expropiativo.
 * - RR: cada proceso usa la CPU como máximo un quantum y vuelve al final de la cola.
 *
 * Métricas por proceso:
 *   Tiempo de retorno   = fin - llegada
 *   Tiempo de espera    = retorno - ráfaga
 *   Tiempo de respuesta = primer inicio - llegada
 */
public class Planificador {

    // ------------------------------------------------------------------
    // Modelos
    // ------------------------------------------------------------------

    static class Proceso {
        final String nombre;
        final int llegada, rafaga, prioridad, orden;

        Proceso(String nombre, int llegada, int rafaga, int prioridad, int orden) {
            this.nombre = nombre;
            this.llegada = llegada;
            this.rafaga = rafaga;
            this.prioridad = prioridad;
            this.orden = orden; // posición original, sirve para desempatar
        }
    }

    static class Segmento {
        final String nombre;
        final int inicio;
        int fin;

        Segmento(String nombre, int inicio, int fin) {
            this.nombre = nombre;
            this.inicio = inicio;
            this.fin = fin;
        }
    }

    static class Resultado {
        final Proceso p;
        final int inicio, fin, retorno, espera, respuesta;

        Resultado(Proceso p, int inicio, int fin) {
            this.p = p;
            this.inicio = inicio;
            this.fin = fin;
            this.retorno = fin - p.llegada;
            this.espera = retorno - p.rafaga;
            this.respuesta = inicio - p.llegada;
        }
    }

    static class Salida {
        final List<Segmento> gantt;
        final List<Resultado> resultados;

        Salida(List<Segmento> gantt, List<Resultado> resultados) {
            this.gantt = gantt;
            this.resultados = resultados;
        }
    }

    interface Algoritmo {
        Salida ejecutar(List<Proceso> procesos);
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Agrega un tramo al Gantt, uniendo tramos contiguos del mismo proceso. */
    static void agregarSegmento(List<Segmento> gantt, String nombre, int inicio, int fin) {
        if (fin <= inicio) return;
        if (!gantt.isEmpty()) {
            Segmento ultimo = gantt.get(gantt.size() - 1);
            if (ultimo.nombre.equals(nombre) && ultimo.fin == inicio) {
                ultimo.fin = fin;
                return;
            }
        }
        gantt.add(new Segmento(nombre, inicio, fin));
    }

    static List<Resultado> calcularResultados(List<Proceso> procesos,
                                              Map<String, Integer> primerInicio,
                                              Map<String, Integer> fin) {
        List<Resultado> resultados = new ArrayList<>();
        for (Proceso p : procesos) {
            resultados.add(new Resultado(p, primerInicio.get(p.nombre), fin.get(p.nombre)));
        }
        return resultados;
    }

    // ------------------------------------------------------------------
    // Algoritmos no expropiativos (FIFO, SJF, PRIORIDAD)
    // ------------------------------------------------------------------

    /** Planificador no expropiativo genérico: elige entre los listos usando el comparador. */
    static Salida noExpropiativo(List<Proceso> procesos, Comparator<Proceso> criterio) {
        List<Proceso> pendientes = new ArrayList<>(procesos);
        List<Segmento> gantt = new ArrayList<>();
        Map<String, Integer> primerInicio = new HashMap<>();
        Map<String, Integer> fin = new HashMap<>();
        int tiempo = 0;

        while (!pendientes.isEmpty()) {
            Proceso elegido = null;
            for (Proceso p : pendientes) {
                if (p.llegada <= tiempo && (elegido == null || criterio.compare(p, elegido) < 0)) {
                    elegido = p;
                }
            }
            if (elegido == null) { // CPU ociosa hasta la próxima llegada
                int siguiente = Integer.MAX_VALUE;
                for (Proceso p : pendientes) siguiente = Math.min(siguiente, p.llegada);
                agregarSegmento(gantt, "IDLE", tiempo, siguiente);
                tiempo = siguiente;
                continue;
            }
            pendientes.remove(elegido);
            primerInicio.put(elegido.nombre, tiempo);
            agregarSegmento(gantt, elegido.nombre, tiempo, tiempo + elegido.rafaga);
            tiempo += elegido.rafaga;
            fin.put(elegido.nombre, tiempo);
        }
        return new Salida(gantt, calcularResultados(procesos, primerInicio, fin));
    }

    static Salida fifo(List<Proceso> procesos) {
        return noExpropiativo(procesos,
                Comparator.<Proceso>comparingInt(p -> p.llegada).thenComparingInt(p -> p.orden));
    }

    static Salida sjf(List<Proceso> procesos) {
        return noExpropiativo(procesos,
                Comparator.<Proceso>comparingInt(p -> p.rafaga)
                        .thenComparingInt(p -> p.llegada).thenComparingInt(p -> p.orden));
    }

    static Salida prioridad(List<Proceso> procesos) {
        return noExpropiativo(procesos,
                Comparator.<Proceso>comparingInt(p -> p.prioridad)
                        .thenComparingInt(p -> p.llegada).thenComparingInt(p -> p.orden));
    }

    // ------------------------------------------------------------------
    // Round Robin
    // ------------------------------------------------------------------

    static Salida roundRobin(List<Proceso> procesos, int quantum) {
        List<Proceso> ordenados = new ArrayList<>(procesos);
        ordenados.sort(Comparator.<Proceso>comparingInt(p -> p.llegada).thenComparingInt(p -> p.orden));
        Deque<Proceso> porLlegar = new ArrayDeque<>(ordenados);
        Deque<Proceso> cola = new ArrayDeque<>();

        Map<String, Integer> restante = new HashMap<>();
        for (Proceso p : procesos) restante.put(p.nombre, p.rafaga);

        List<Segmento> gantt = new ArrayList<>();
        Map<String, Integer> primerInicio = new HashMap<>();
        Map<String, Integer> fin = new HashMap<>();
        int tiempo = 0;

        while (!porLlegar.isEmpty() && porLlegar.peekFirst().llegada <= tiempo) {
            cola.addLast(porLlegar.pollFirst());
        }

        while (!cola.isEmpty() || !porLlegar.isEmpty()) {
            if (cola.isEmpty()) { // CPU ociosa
                int siguiente = porLlegar.peekFirst().llegada;
                agregarSegmento(gantt, "IDLE", tiempo, siguiente);
                tiempo = siguiente;
                while (!porLlegar.isEmpty() && porLlegar.peekFirst().llegada <= tiempo) {
                    cola.addLast(porLlegar.pollFirst());
                }
                continue;
            }

            Proceso p = cola.pollFirst();
            primerInicio.putIfAbsent(p.nombre, tiempo);
            int uso = Math.min(quantum, restante.get(p.nombre));
            agregarSegmento(gantt, p.nombre, tiempo, tiempo + uso);
            tiempo += uso;
            restante.put(p.nombre, restante.get(p.nombre) - uso);

            // los que llegaron durante la ejecución entran antes que el expropiado
            while (!porLlegar.isEmpty() && porLlegar.peekFirst().llegada <= tiempo) {
                cola.addLast(porLlegar.pollFirst());
            }
            if (restante.get(p.nombre) > 0) {
                cola.addLast(p);
            } else {
                fin.put(p.nombre, tiempo);
            }
        }
        return new Salida(gantt, calcularResultados(procesos, primerInicio, fin));
    }

    // ------------------------------------------------------------------
    // Presentación
    // ------------------------------------------------------------------

    static String repetir(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    static String centrar(String s, int ancho) {
        int total = ancho - s.length();
        if (total <= 0) return s;
        int izq = total / 2;
        return repetir(" ", izq) + s + repetir(" ", total - izq);
    }

    static void mostrarGantt(List<Segmento> gantt) {
        StringBuilder barras = new StringBuilder("|");
        StringBuilder marcas = new StringBuilder();
        for (Segmento s : gantt) {
            int ancho = Math.max(s.nombre.length() + 2, (s.fin - s.inicio) * 2);
            barras.append(centrar(s.nombre, ancho)).append("|");
            marcas.append(String.format("%-" + (ancho + 1) + "s", s.inicio));
        }
        marcas.append(gantt.get(gantt.size() - 1).fin);
        System.out.println(barras);
        System.out.println(marcas);
    }

    static void mostrarTabla(List<Resultado> resultados, boolean usaPrioridad) {
        String cab = String.format("%-6s%5s%5s", "Proc", "Lleg", "Ráf");
        if (usaPrioridad) cab += String.format("%6s", "Prio");
        cab += String.format("%8s%6s%9s%8s%6s", "Inicio", "Fin", "Retorno", "Espera", "Resp");
        System.out.println(cab);
        System.out.println(repetir("-", cab.length()));
        for (Resultado r : resultados) {
            String fila = String.format("%-6s%5d%5d", r.p.nombre, r.p.llegada, r.p.rafaga);
            if (usaPrioridad) fila += String.format("%6d", r.p.prioridad);
            fila += String.format("%8d%6d%9d%8d%6d", r.inicio, r.fin, r.retorno, r.espera, r.respuesta);
            System.out.println(fila);
        }
    }

    static double[] promedios(List<Resultado> resultados) {
        double ret = 0, esp = 0, res = 0;
        for (Resultado r : resultados) {
            ret += r.retorno;
            esp += r.espera;
            res += r.respuesta;
        }
        int n = resultados.size();
        return new double[]{ret / n, esp / n, res / n};
    }

    static double[] ejecutar(String titulo, Algoritmo alg, List<Proceso> procesos, boolean usaPrioridad) {
        System.out.println("\n" + repetir("=", 60));
        System.out.println(titulo);
        System.out.println(repetir("=", 60));
        Salida salida = alg.ejecutar(procesos);
        mostrarGantt(salida.gantt);
        System.out.println();
        mostrarTabla(salida.resultados, usaPrioridad);
        double[] prom = promedios(salida.resultados);
        System.out.printf("%nPromedios -> Retorno: %.2f | Espera: %.2f | Respuesta: %.2f%n",
                prom[0], prom[1], prom[2]);
        return prom;
    }

    // ------------------------------------------------------------------
    // Entrada de datos
    // ------------------------------------------------------------------

    static int leerEntero(Scanner sc, String mensaje, int minimo) {
        while (true) {
            System.out.print(mensaje);
            try {
                int valor = Integer.parseInt(sc.nextLine().trim());
                if (valor >= minimo) return valor;
            } catch (NumberFormatException e) {
                // se vuelve a pedir
            }
            System.out.println("  Ingresa un número entero >= " + minimo + ".");
        }
    }

    static List<Proceso> procesosEjemplo() {
        List<Proceso> l = new ArrayList<>();
        l.add(new Proceso("P1", 0, 8, 3, 0));
        l.add(new Proceso("P2", 1, 4, 1, 1));
        l.add(new Proceso("P3", 2, 9, 4, 2));
        l.add(new Proceso("P4", 3, 5, 2, 3));
        return l;
    }

    static List<Proceso> pedirProcesos(Scanner sc) {
        int n = leerEntero(sc, "Número de procesos: ", 1);
        List<Proceso> l = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            System.out.println("Proceso P" + i);
            int llegada = leerEntero(sc, "  Tiempo de llegada: ", 0);
            int rafaga = leerEntero(sc, "  Ráfaga de CPU: ", 1);
            int prio = leerEntero(sc, "  Prioridad (menor número = más prioridad): ", 0);
            l.add(new Proceso("P" + i, llegada, rafaga, prio, i - 1));
        }
        return l;
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("PLANIFICACIÓN DE PROCESOS: FIFO, SJF, PRIORIDAD y RR");
        System.out.print("¿Usar datos de ejemplo? (s/n): ");
        boolean ejemplo = sc.nextLine().trim().equalsIgnoreCase("s");
        List<Proceso> procesos = ejemplo ? procesosEjemplo() : pedirProcesos(sc);
        final int quantum = leerEntero(sc, "Quantum para Round Robin: ", 1);

        System.out.println("\nProcesos de entrada:");
        for (Proceso p : procesos) {
            System.out.println("  " + p.nombre + ": llegada=" + p.llegada
                    + ", ráfaga=" + p.rafaga + ", prioridad=" + p.prioridad);
        }

        Map<String, double[]> resumen = new LinkedHashMap<>();
        resumen.put("FIFO", ejecutar("FIFO (First In, First Out)",
                Planificador::fifo, procesos, false));
        resumen.put("SJF", ejecutar("SJF (Shortest Job First, no expropiativo)",
                Planificador::sjf, procesos, false));
        resumen.put("PRIORIDAD", ejecutar("PRIORIDAD (no expropiativo, menor número = mayor prioridad)",
                Planificador::prioridad, procesos, true));
        resumen.put("RR", ejecutar("ROUND ROBIN (quantum = " + quantum + ")",
                ps -> roundRobin(ps, quantum), procesos, false));

        System.out.println("\n" + repetir("=", 60));
        System.out.println("COMPARACIÓN (tiempos promedio)");
        System.out.println(repetir("=", 60));
        System.out.printf("%-12s%10s%10s%12s%n", "Algoritmo", "Retorno", "Espera", "Respuesta");
        for (Map.Entry<String, double[]> e : resumen.entrySet()) {
            double[] v = e.getValue();
            System.out.printf("%-12s%10.2f%10.2f%12.2f%n", e.getKey(), v[0], v[1], v[2]);
        }
    }
}