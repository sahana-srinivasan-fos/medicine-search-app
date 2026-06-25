from sqlalchemy import (
    Column,
    Integer,
    String,
    Float,
    Date,
    DateTime,
    ForeignKey
)

from database import Base
from sqlalchemy.orm import relationship
from datetime import date

class MedicineMaster(Base):
    __tablename__ = "medicine_master"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, index=True)
    manufacturer = Column(String)
    category = Column(String)
    # One-to-one (logical) relationship to Inventory
    inventory = relationship("Inventory", back_populates="medicine", uselist=False)

class Inventory(Base):
    __tablename__ = "inventory"

    id = Column(Integer, primary_key=True, index=True)

    medicine_id = Column(
        Integer,
        ForeignKey("medicine_master.id")
    )
    # optional batch identifier for real inventory implementations
    batch_number = Column(String, nullable=True)

    stock_quantity = Column(Integer, default=0)

    tablets_per_strip = Column(Integer, default=10)

    selling_price = Column(Float, default=0.0)

    expiry_date = Column(Date, nullable=True)

    # relationship back to medicine
    medicine = relationship("MedicineMaster", back_populates="inventory")

class Cart(Base):
    __tablename__ = "cart"

    id = Column(Integer, primary_key=True, index=True)

    created_at = Column(DateTime)

    status = Column(String)

class CartItem(Base):
    __tablename__ = "cart_items"

    id = Column(Integer, primary_key=True, index=True)

    cart_id = Column(
        Integer,
        ForeignKey("cart.id")
    )

    medicine_id = Column(
        Integer,
        ForeignKey("medicine_master.id")
    )

    quantity = Column(Integer)

class Order(Base):
    __tablename__ = "orders"

    id = Column(Integer, primary_key=True, index=True)
    total_amount = Column(Float)
    discount = Column(Float, default=0.0)
    gst_percent = Column(Float, default=0.0)
    gst_amount = Column(Float, default=0.0)
    subtotal = Column(Float, default=0.0)
    created_at = Column(DateTime)

    items = relationship("OrderItem", back_populates="order", cascade="all, delete-orphan")

class OrderItem(Base):
    __tablename__ = "order_items"

    id = Column(Integer, primary_key=True, index=True)

    order_id = Column(
        Integer,
        ForeignKey("orders.id")
    )

    medicine_id = Column(
        Integer,
        ForeignKey("medicine_master.id")
    )

    quantity = Column(Integer)
    price = Column(Float)

    order = relationship("Order", back_populates="items")
    medicine = relationship("MedicineMaster")
