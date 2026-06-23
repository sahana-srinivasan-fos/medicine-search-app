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

class MedicineMaster(Base):
    __tablename__ = "medicine_master"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, index=True)
    manufacturer = Column(String)
    category = Column(String)

class Inventory(Base):
    __tablename__ = "inventory"

    id = Column(Integer, primary_key=True, index=True)

    medicine_id = Column(
        Integer,
        ForeignKey("medicine_master.id")
    )

    batch_number = Column(String)

    stock_quantity = Column(Integer)

    tablets_per_strip = Column(Integer)

    selling_price = Column(Float)

    expiry_date = Column(Date)

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

    created_at = Column(DateTime)

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
