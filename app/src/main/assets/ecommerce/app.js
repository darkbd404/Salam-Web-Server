const PRODUCTS = [
    { id: 1, name: "CyberPhone Ultra 5G", cat: "phones", price: 699, badge: "🔥 Best Seller", icon: "📱", desc: "120Hz OLED, 256GB Storage, AI Camera, 5000mAh Battery" },
    { id: 2, name: "QuantumBook Pro M3", cat: "laptops", price: 1299, badge: "⚡ Top Pick", icon: "💻", desc: "16-core CPU, 32GB RAM, 1TB SSD, 16-inch Liquid Retina Display" },
    { id: 3, name: "SonicNoise ANC Headphones", cat: "audio", price: 199, badge: "🎧 Hi-Res", icon: "🎧", desc: "Active Noise Cancellation, 40h Battery, Spatial Audio" },
    { id: 4, name: "PulseTrack Smart Watch V4", cat: "wearables", price: 149, badge: "⭐ 4.9 Rating", icon: "⌚", desc: "SpO2 & ECG Monitor, GPS, 5ATM Waterproof, AMOLED Screen" },
    { id: 5, name: "NovaBuds Pro Wireless", cat: "audio", price: 89, badge: "✨ New", icon: "🎵", desc: "Ultra-low latency, Bluetooth 5.4, Wireless Qi Fast Charging" },
    { id: 6, name: "TitanPower 65W GaN Charger", cat: "phones", price: 39, badge: "⚡ Fast Charge", icon: "🔌", desc: "3x USB-C Ports, Compact Travel Size, Universal Compatibility" },
    { id: 7, name: "SlimPad Pro 11-inch Tablet", cat: "laptops", price: 449, badge: "🎨 Creator Ready", icon: "📟", desc: "Stylus Pen support, 8GB RAM, Quad Speakers, 2K Resolution" },
    { id: 8, name: "Apex Mechanical Keyboard", cat: "laptops", price: 119, badge: "🕹️ RGB", icon: "⌨️", desc: "Hot-swappable switches, Aluminum chassis, Tri-mode Wireless" }
];

let cart = [];
let currentCategory = 'all';

function renderProducts() {
    const grid = document.getElementById('products-section');
    const query = (document.getElementById('search-input').value || '').toLowerCase();

    const filtered = PRODUCTS.filter(p => {
        const matchesCat = currentCategory === 'all' || p.cat === currentCategory;
        const matchesQuery = p.name.toLowerCase().includes(query) || p.desc.toLowerCase().includes(query);
        return matchesCat && matchesQuery;
    });

    if (filtered.length === 0) {
        grid.innerHTML = '<div style="grid-column: 1/-1; text-align:center; padding: 40px; color: #8da9c4;">No products found matching your search.</div>';
        return;
    }

    grid.innerHTML = filtered.map(p => `
        <div class="product-card">
            ${p.badge ? `<span class="product-badge">${p.badge}</span>` : ''}
            <div class="product-thumb">${p.icon}</div>
            <div class="product-name">${p.name}</div>
            <div class="product-desc">${p.desc}</div>
            <div class="price-row">
                <span class="price">$${p.price.toFixed(2)}</span>
                <button class="add-btn" onclick="addToCart(${p.id})">Add to Cart 🛒</button>
            </div>
        </div>
    `).join('');
}

function setCategory(cat, btn) {
    currentCategory = cat;
    document.querySelectorAll('.cat-pill').forEach(b => b.classList.remove('active'));
    if (btn) btn.classList.add('active');
    renderProducts();
}

function filterProducts() {
    renderProducts();
}

function scrollToProducts() {
    document.getElementById('products-section').scrollIntoView({ behavior: 'smooth' });
}

function addToCart(productId) {
    const item = PRODUCTS.find(p => p.id === productId);
    if (!item) return;

    const existing = cart.find(c => c.id === productId);
    if (existing) {
        existing.qty++;
    } else {
        cart.push({ ...item, qty: 1 });
    }

    updateCartUI();
    // Brief animation feedback on cart button
    const btn = document.querySelector('.cart-btn');
    btn.style.transform = 'scale(1.15)';
    setTimeout(() => btn.style.transform = '', 200);
}

function updateCartUI() {
    const totalQty = cart.reduce((sum, item) => sum + item.qty, 0);
    const totalPrice = cart.reduce((sum, item) => sum + (item.price * item.qty), 0);

    document.getElementById('cart-badge').textContent = totalQty;
    document.getElementById('cart-total').textContent = `$${totalPrice.toFixed(2)}`;
    document.getElementById('checkout-total').textContent = `$${totalPrice.toFixed(2)}`;
    document.getElementById('checkout-btn').disabled = cart.length === 0;

    const list = document.getElementById('cart-items');
    if (cart.length === 0) {
        list.innerHTML = '<p class="empty-msg" style="text-align:center; color:#8da9c4; padding:30px 0;">Your shopping cart is empty.</p>';
        return;
    }

    list.innerHTML = cart.map(item => `
        <div class="cart-row">
            <div class="cart-info">
                <b>${item.icon} ${item.name}</b>
                <small>$${item.price.toFixed(2)} each</small>
            </div>
            <div class="qty-controls">
                <button class="qty-btn" onclick="changeQty(${item.id}, -1)">-</button>
                <span style="font-weight:700; min-width:20px; text-align:center;">${item.qty}</span>
                <button class="qty-btn" onclick="changeQty(${item.id}, 1)">+</button>
            </div>
            <b style="color:var(--green); min-width:60px; text-align:right;">$${(item.price * item.qty).toFixed(2)}</b>
        </div>
    `).join('');
}

function changeQty(id, delta) {
    const item = cart.find(c => c.id === id);
    if (!item) return;

    item.qty += delta;
    if (item.qty <= 0) {
        cart = cart.filter(c => c.id !== id);
    }
    updateCartUI();
}

function toggleCart() {
    const modal = document.getElementById('cart-modal');
    modal.classList.toggle('open');
}

function closeCart(e) {
    if (e.target.id === 'cart-modal') {
        toggleCart();
    }
}

function openCheckout() {
    toggleCart();
    document.getElementById('checkout-modal').classList.add('open');
}

function toggleCheckout() {
    document.getElementById('checkout-modal').classList.toggle('open');
}

function closeCheckout(e) {
    if (e.target.id === 'checkout-modal') {
        toggleCheckout();
    }
}

function submitOrder(e) {
    e.preventDefault();
    const name = document.getElementById('cust-name').value;
    const phone = document.getElementById('cust-phone').value;
    const addr = document.getElementById('cust-address').value;
    const pay = document.getElementById('cust-payment').value;
    const total = document.getElementById('checkout-total').textContent;

    document.getElementById('checkout-modal').classList.remove('open');
    document.getElementById('success-msg').textContent = 
        `Thank you ${name}! Your order totaling ${total} has been confirmed. Delivery to: ${addr}.`;
    document.getElementById('order-success-modal').classList.add('open');
}

function resetAfterOrder() {
    cart = [];
    updateCartUI();
    document.getElementById('order-success-modal').classList.remove('open');
    document.getElementById('checkout-form').reset();
}

// Initial setup
renderProducts();
updateCartUI();
