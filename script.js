const $ = id => document.getElementById(id);
const money = v => v.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
const esc = t => String(t).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const weekdayOf = value => new Date(value + "T12:00:00").getDay();

/* ========== LOJA ========== */

// [categoria, ícone, nome, descrição, preço]
const products = [
    ["food", "🐶", "Ração para filhotes", "Alimentação para cães em fase de crescimento.", 79.9],
    ["food", "🐕", "Ração para cães adultos", "Alimentação para cães adultos.", 89.9],
    ["food", "🐕‍🦺", "Ração para cães idosos", "Opções para a fase sênior.", 99.9],
    ["food", "🐈", "Ração para gatos", "Alimentação para gatos adultos.", 74.9],
    ["food", "🐾", "Ração para gatos filhotes", "Alimentação para a fase de crescimento felino.", 82.9],
    ["food", "🥫", "Ração úmida", "Sachês e alimentos úmidos para cães e gatos.", 6.5],
    ["food", "✨", "Ração Premium", "Alternativas premium para seu pet.", 119.9],
    ["food", "🩺", "Alimentação especial", "Dietas específicas com orientação veterinária.", 139.9],
    ["item", "🦴", "Petiscos", "Petiscos para momentos especiais.", 15],
    ["item", "🦮", "Guia e Coleira", "Kit passeio para seu companheiro.", 39.9],
    ["item", "🧸", "Brinquedos", "Brinquedos para diversão e enriquecimento.", 25],
    ["item", "🛏️", "Caminha", "Conforto para descansar.", 89.9],
    ["item", "🥤", "Potes de alimentação", "Potes para água e comida.", 29.9],
    ["item", "🧴", "Kit Higiene Pet", "Shampoo, escova e produtos de higiene.", 22.9]
];

products.forEach(([type, icon, name, desc, price], i) => {
    $(type === "food" ? "foodGrid" : "itemsGrid").insertAdjacentHTML("beforeend", `
        <div class="shop-card">
            <div class="emoji">${icon}</div>
            <h3>${name}</h3>
            <p>${desc}</p>
            <span class="price">${money(price)}</span>
            <button class="btn" data-add="${i}">Adicionar</button>
        </div>`);
});

/* ========== CARRINHO ========== */

const DELIVERY_FEE = 8;
const cart = {}; // { idDoProduto: quantidade }
const isDelivery = () => document.querySelector('[name="deliveryOption"]:checked').value === "entrega";

function renderCart() {
    const ids = Object.keys(cart);
    const subtotal = ids.reduce((sum, i) => sum + products[i][4] * cart[i], 0);
    const fee = ids.length && isDelivery() ? DELIVERY_FEE : 0;

    $("cartCount").textContent = ids.reduce((sum, i) => sum + cart[i], 0);

    $("cartItems").innerHTML = ids.length
        ? ids.map(i => `
            <div class="cart-item">
                <span>${products[i][2]}<br>${money(products[i][4])} × ${cart[i]}</span>
                <span>${money(products[i][4] * cart[i])}<button data-remove="${i}" aria-label="Remover um">−</button></span>
            </div>`).join("")
        : "Seu carrinho está vazio.";

    $("cartTotal").innerHTML = `Produtos: ${money(subtotal)}<br>Entrega: ${money(fee)}<br><strong>Total: ${money(subtotal + fee)}</strong>`;
}

document.querySelectorAll('[name="deliveryOption"]').forEach(radio =>
    radio.addEventListener("change", () => {
        $("deliveryAddress").hidden = !isDelivery();
        renderCart();
    })
);

renderCart();

/* ========== CALENDÁRIO DE VACINAÇÃO ========== */

// Chave = dia do mês
const campaigns = {
    1:  ["Vacinação e avaliação preventiva", "Dia de avaliação da carteirinha e orientação sobre vacinas para cães e gatos."],
    5:  ["Campanha de vacinação", "Atendimento para avaliação e atualização do calendário vacinal."],
    10: ["Vacinação felina", "Orientações e avaliação para vacinação dos gatos."],
    15: ["Vacinação canina", "Avaliação veterinária e orientação para vacinação dos cães."],
    20: ["Dia de prevenção", "Revisão dos cuidados preventivos e da carteirinha de vacinação."],
    25: ["Campanha de vacinação", "Atendimento para avaliação e orientação sobre imunização."]
};

let view = new Date();
let selected = null;

function renderCalendar() {
    const year = view.getFullYear();
    const month = view.getMonth();
    const today = new Date();

    $("calendarMonth").textContent = new Date(year, month).toLocaleDateString("pt-BR", { month: "long", year: "numeric" });

    let html = ["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"].map(d => `<b>${d}</b>`).join("");
    html += '<div class="day empty"></div>'.repeat(new Date(year, month, 1).getDay());

    for (let day = 1; day <= new Date(year, month + 1, 0).getDate(); day++) {
        const date = `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
        const isToday = today.getFullYear() === year && today.getMonth() === month && today.getDate() === day;
        const classes = ["day", isToday && "today", campaigns[day] && "vaccine", selected === date && "selected"].filter(Boolean).join(" ");

        html += `<button class="${classes}" data-date="${date}">${day}${campaigns[day] ? "<small>💉 Vacinação</small>" : ""}</button>`;
    }

    $("calendarGrid").innerHTML = html;
}

function showVaccineInfo(date) {
    const campaign = campaigns[+date.slice(8)];
    const formatted = new Date(date + "T12:00:00").toLocaleDateString("pt-BR", { day: "numeric", month: "long", year: "numeric" });

    $("vaccineInfo").innerHTML = campaign
        ? `<h3>💉 ${campaign[0]}</h3>
           <p>${campaign[1]}</p>
           <p class="date">📅 ${formatted}</p>
           <p>Entre em contato para confirmar a disponibilidade e quais vacinas são indicadas para seu pet.</p>`
        : `<h3>📅 ${formatted}</h3>
           <p>Não há campanha cadastrada para este dia. Entre em contato com a clínica para consultar outras datas de vacinação.</p>`;
}

renderCalendar();

/* ========== AGENDAMENTO E EQUIPE ========== */

const staff = [
    ["Davi", "Atendimento animal"],
    ["Diego", "Atendimento animal"],
    ["Ana Karolina", "Caixa / Atendimento"],
    ["Marcela", "Atendimento animal"],
    ["Nicole", "Atendimento animal / caixa"]
];

// Chave = dia da semana (1 = segunda ... 6 = sábado)
const teamByDay = {
    1: { animals: ["Davi", "Marcela"],         cashier: "Ana Karolina" },
    2: { animals: ["Diego", "Nicole"],         cashier: "Davi" },
    3: { animals: ["Ana Karolina", "Marcela"], cashier: "Diego" },
    4: { animals: ["Davi", "Nicole"],          cashier: "Marcela" },
    5: { animals: ["Diego", "Ana Karolina"],   cashier: "Nicole" },
    6: { animals: ["Marcela", "Nicole"],       cashier: "Ana Karolina" }
};

$("staffGrid").innerHTML = staff.map(([name, role]) => `<div class="staff-card"><strong>${name}</strong><small>${role}</small></div>`).join("");

for (let h = 8; h <= 19; h++) {
    const time = `${String(h).padStart(2, "0")}:00`;
    $("timeSelect").insertAdjacentHTML("beforeend", `<option>${time}</option>`);
}

const bookingDate = $("bookingDate");
bookingDate.min = new Date().toLocaleDateString("sv-SE"); // AAAA-MM-DD no horário local

bookingDate.addEventListener("change", () => {
    if (!bookingDate.value) return;

    const team = teamByDay[weekdayOf(bookingDate.value)];

    if (!team) {
        alert("A clínica funciona de segunda a sábado.");
        bookingDate.value = "";
        $("staffForDate").textContent = "Selecione uma data para consultar a equipe.";
        return;
    }

    $("staffForDate").innerHTML = `<strong>Equipe do dia:</strong><br>🐾 Cuidado animal: ${team.animals.join(" e ")}<br>💳 Caixa: ${team.cashier}`;
});

$("bookingForm").addEventListener("submit", e => {
    e.preventDefault();

    const data = new FormData(e.target);
    const team = teamByDay[weekdayOf(data.get("date"))];
    const get = key => esc(data.get(key));

    if (!team) return alert("A clínica funciona de segunda a sábado.");

    $("bookingSuccess").hidden = false;
    $("bookingSuccess").innerHTML = `
        <strong>Solicitação preparada! ✨</strong><br>
        Tutor: ${get("tutor")}<br>
        Pet: ${get("pet")} (${get("species")})<br>
        Serviço: ${get("service")}<br>
        Data: ${new Date(data.get("date") + "T12:00:00").toLocaleDateString("pt-BR")} às ${get("time")}<br>
        Pagamento: ${get("payment")}<br>
        Equipe: ${team.animals.join(" e ")} — Caixa: ${team.cashier}<br>
        Observações: ${esc(data.get("notes") || "Nenhuma")}<br><br>
        Solicitação demonstrativa — aguarde confirmação da clínica.`;
});

/* ========== EVENTOS DE CLIQUE (carrinho e calendário) ========== */

document.addEventListener("click", e => {
    const add = e.target.closest("[data-add]");
    const remove = e.target.closest("[data-remove]");
    const day = e.target.closest("[data-date]");
    const month = e.target.closest("[data-month]");

    if (add) {
        cart[add.dataset.add] = (cart[add.dataset.add] || 0) + 1;
        renderCart();
    }

    if (remove) {
        if (--cart[remove.dataset.remove] <= 0) delete cart[remove.dataset.remove];
        renderCart();
    }

    if (month) {
        view = new Date(view.getFullYear(), view.getMonth() + +month.dataset.month, 1);
        renderCalendar();
    }

    if (day) {
        selected = day.dataset.date;
        renderCalendar();
        showVaccineInfo(selected);
    }
});

/* ========== ANIMAÇÃO DE ENTRADA ========== */

const observer = new IntersectionObserver(entries => entries.forEach(entry => {
    if (entry.isIntersecting) {
        entry.target.classList.add("show");
        observer.unobserve(entry.target);
    }
}), { threshold: 0.1 });

document.querySelectorAll("section").forEach(section => {
    section.classList.add("reveal");
    observer.observe(section);
});
