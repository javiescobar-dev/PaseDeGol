// importar las funciones de la v2 (2ª Generacion)
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

// definir que vamos a usar la clave secreta de Stripe configurado en Firebase
const stripeSecret = defineSecret("STRIPE_SECRET");
const stripe = require("stripe");

exports.createPaymentIntent = onCall({ secrets: [stripeSecret] }, async (request) => {
    // verificar que la peticion viene de un usuario logueado en la app
    if (!request.auth) {
        throw new HttpsError(
            'unauthenticated',
            'Debes iniciar sesión para realizar pagos.'
        );
    }

    // obtener la clave secreta de forma segura y montar el cliente de Stripe
    const stripeKey = stripeSecret.value();
    const stripeClient = stripe(stripeKey);

    // leer los datos que vienen desde la app en Android
    const amount = request.data.amount;

    // validar que el importe es un numero entero positivo (en centimos)
    if (!amount || typeof amount !== "number" || amount <= 0 || !Number.isInteger(amount)) {
        throw new HttpsError(
            'invalid-argument',
            'El importe debe ser un número entero positivo en céntimos.'
        );
    }
    try {
        // crear el intento de pago (PaymentIntent)
        const paymentIntent = await stripeClient.paymentIntents.create({
            amount: amount,
            currency: "eur", // forzamos euros
            automatic_payment_methods: { enabled: true },
        });

        // devolver el cliente_secret del intento de pago a Android para cobrar esta cantidad
        return {
            clientSecret: paymentIntent.client_secret,
        };
    } catch (error) {
        console.error("Error en Stripe:", error);
        throw new HttpsError('internal', error.message);
    }
});
